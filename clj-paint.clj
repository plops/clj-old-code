;; Having watched some screen captures of speed painting on Youtube I am jealous and want to be able to do the same thing.

;; I organized myself a pressure sensitive graphic tablet but I don't feel like installing Gimp on my EEEPC. Lets see how far I can get with Clojure as a replacement.
;; Details

;; The graphic tablet generates time stamped 16 byte packets on /dev/input/event6 that either contain absolute x or y coordinate or the pressure. However to keep things compatible I will start with a normal mouse as input.

;; Run hexdump -C /dev/input/event3 and move the mouse. You should see the packages it emits when its moved. If you see no output try a device with a different number. On my Ubuntu computer I had to add Option "AutoAddDevices" "false" to the section ServerFlags.

;; The structure of the data is described in /usr/include/linux/input.h with:

;; struct input_event {
;;         struct timeval time;
;;         __u16 type;
;;         __u16 code;
;;         __s32 value;
;; };

;; Lets see what the data looks like. I open a new Clojure file in Emacs with Slime and Paredit. The following lines open the file and read one packet. Run each line with C-M-x.

(set! *warn-on-reflection* true)
(import '(java.io FileInputStream))
(def f (FileInputStream. "/dev/input/event3"))
(def buf (make-array Byte/TYPE 16))
(.read f buf)

;; The line with *warn-on-reflection* is supposed to protect me from writing horribly slow code.

;; To see the data put the cursor on buf and press C-c I. This opens an Inspector window with the following contents:

;; Type: class [B
;; Value: [B@1a73d30

;; But that isn't very readable. I use the following code to parse the data into its components.

(defn char-to-uchar [c]
  (if (< c 0)
    (+ 256 c)
    c))

(defn read-uint [#^bytes buf #^Integer offset]
  (let [a (char-to-uchar (aget buf (int (+ (int 0) offset))))
        b (char-to-uchar (aget buf (int (+ (int 1) offset))))
        c (char-to-uchar (aget buf (int (+ (int 2) offset))))
        d (char-to-uchar (aget buf (int (+ (int 3) offset))))
        value (+ a
                 (* 256 (+ b
                           (* 256 (+ c
                                     (* 256 d))))))]
    value))

(defn read-ushort [#^bytes buf #^Integer offset]
  (let [a (char-to-uchar (aget buf (int (+ 0 offset))))
        b (char-to-uchar (aget buf (int (+ 1 offset))))
        value (+ (int a)
                 (* 256 (int b)))]
    value))

(defn read-input-event [#^bytes buf]
  (let [tv-sec (read-uint buf 0)
        tv-usec (read-uint buf 4)
        type (read-ushort buf 8)
        code (read-ushort buf 10)
        value (read-uint buf 12)]
    {:sec tv-sec :usec tv-usec :type type :code code :value value}))

(read-input-event buf)

;; Executing the last line gives {:sec 1246284491, :usec 69571, :type 2, :code 0, :value 1}.

;; This code looks a bit big for what it does but I don't know any better way to parse the unsigned data. Actually I am not even sure if it is correct. In the tests I did. It worked okay.

;; To find out more about the protocol I look at a few more events:

(dotimes [i 100]
  (.read f buf)
  (println (read-input-event buf)))

;; {:sec 1246286269, :usec 474964, :type 2, :code 0, :value 2}
;; {:sec 1246286269, :usec 474968, :type 2, :code 1, :value 3}
;; {:sec 1246286269, :usec 474975, :type 0, :code 0, :value 0}
;; {:sec 1246286269, :usec 482963, :type 2, :code 0, :value 3}
;; {:sec 1246286269, :usec 482967, :type 2, :code 1, :value 4}
;; {:sec 1246286269, :usec 482975, :type 0, :code 0, :value 0}
;; {:sec 1246286269, :usec 490965, :type 2, :code 0, :value 8}
;; {:sec 1246286269, :usec 490969, :type 2, :code 1, :value 8}
;; {:sec 1246286269, :usec 490976, :type 0, :code 0, :value 0}
;; {:sec 1246286269, :usec 498965, :type 2, :code 0, :value 9}
;; {:sec 1246286269, :usec 498968, :type 2, :code 1, :value 9}
;; {:sec 1246286269, :usec 498976, :type 0, :code 0, :value 0}
;; {:sec 1246286269, :usec 506966, :type 2, :code 0, :value 4}
;; {:sec 1246286269, :usec 506970, :type 2, :code 1, :value 4}
;; {:sec 1246286269, :usec 506977, :type 0, :code 0, :value 0}
;; {:sec 1246286269, :usec 514966, :type 2, :code 0, :value 6}
;; {:sec 1246286269, :usec 514970, :type 2, :code 1, :value 6}
;; {:sec 1246286269, :usec 514977, :type 0, :code 0, :value 0}

;; The :type 2 seems to encode differential movement and :code is probably direction. Sometimes there are other events that might code the buttons. I don't care about them.

;; Now I want to see something on the screen. I decided to use JOGL for that. I unzipped the file jogl-1.1.2-pre-20080523-linux-i586.zip and copied all the .so and .jar files into ~/clojure. To run the examples from Slime I call export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:~/clojure before I start Emacs.

;; The following is pretty much the minimal code to draw something on the screen. Select all the code in the file and press C-c C-l to run it. A window with a white cross in the center should appear.

(add-classpath "file:///home/martin/clojure/jogl.jar")
(add-classpath "file:///home/martin/clojure/gluegen-rt.jar")
(import '(java.awt Frame)
        '(java.awt.event WindowListener WindowAdapter KeyListener KeyEvent MouseMotionListener)
        '(javax.media.opengl GLCanvas GLEventListener GL GLAutoDrawable)
        '(com.sun.opengl.util Animator))

(def win-w 720)
(def win-h 450)

(def canvas (GLCanvas.))
(def frame (Frame. "Draw"))
(def animator (Animator. canvas))
(defn newshape 
  "Called when window is resized."
  [#^GLAutoDrawable drawable x y w h]
  (doto (.getGL drawable)
    (.glViewport 0 0 w h)
    (.glMatrixMode (. GL GL_PROJECTION))
    (.glLoadIdentity)
    (.glOrtho 0 w h 0 -1 1)
    (.glMatrixMode (. GL GL_MODELVIEW))
    (.glLoadIdentity)))

(defn draw 
  "Updates the contents of the screen."
  [#^GLAutoDrawable drawable]
  (let [d 40] 
    (doto (.getGL drawable)
      (.glClear (. GL GL_COLOR_BUFFER_BIT))
      (.glLoadIdentity)
      (.glTranslatef (/ win-w 2) (/ win-h 2) 0)
      (.glBegin (. GL GL_LINES))
      (.glColor3f 1 1 1)
      (.glVertex3f d 0 0)
      (.glVertex3f (- d) 0 0)
      (.glVertex3f 0 d 0)
      (.glVertex3f 0 (- d) 0)
      (.glEnd)))
  (. Thread (sleep 40)))

(defn exit []
  (.stop animator)
  (.dispose frame))


(.addGLEventListener 
 canvas
 (proxy [GLEventListener] []
   (display [#^GLAutoDrawable drawable]
            (draw drawable))
   (displayChanged [#^GLAutoDrawable drawable m d])
   (reshape [#^GLAutoDrawable drawable x y w h]
            (newshape drawable x y w h))
   (init [#^GLAutoDrawable drawable]
         (.addKeyListener drawable
                          (proxy [KeyListener] []
                            (keyPressed 
                             [e]
                             (when (= (.getKeyCode e)
                                      (. KeyEvent VK_ESCAPE))
                               (exit))))))))

(doto frame
  (.add canvas)
  (.setSize win-w win-h)
  (.setVisible true))

(.start animator)

;; The draw function is called every 40 ms and can be updated on the fly (try changing the color and redefine the function with C-M-x). The window can be closed by pressing the escape key. It can then be reopened by calling the last two expressions. 
