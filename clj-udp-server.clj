;; This is a small UDP server in Clojure. It will listen on port 1234 for a 12 byte package and echos it.
;; Details


(import '(java.io IOException)
        '(java.net DatagramPacket DatagramSocket SocketException))

(try (def socket (DatagramSocket. 1234))
     (catch SocketException e
       (println "error: can't create socket")))

(def buf (make-array (. Byte TYPE) 12))
(def packet (DatagramPacket. buf 12))

(try (.receive socket packet)
     (dotimes [i 12] 
       (prn (aget buf i)))
     (let [reply (DatagramPacket. buf 12
                                  (.getAddress packet)
                                  (.getPort packet))]
       (.send socket reply))
     (catch IOException e
       (println "error in receiving packet")))

(.close socket)

;; A testrun:

;; martin@torben:~$ netcat -u localhost 1234 
;; hallo
;; hallo
