;; CUDA makes the processing power of NVIDIA graphics cards available for normal computation. I thought it would be cool to have that available in the REPL.
;; Details

;; I extracted the following Java library http://www.gass-ltd.co.il/Download.aspx?file=jcuda/1.1/jcuda_1.1_linux64.zip and start Emacs after including the path of the libraries into LD_LIBRARY_PATH.

;; This is a straight forward translation of the example code that comes with the library. It shows how to copy an array of integers to the graphics card call a parallel program and get the results back into main memory.

(add-classpath "file:///home/martin/jcuda/jcuda/jcuda.jar")
(import '(jcuda CUDA NativeUtils)
        '(jcuda.driver.types CUdeviceptr CUstream))

(def cuda (CUDA. true))

(.getDevice cuda 0)
(.createContext cuda)
(.loadModule cuda "/home/martin/0630/test_module.cubin")
(.getModuleFunction cuda "Add")
(def memSize (* (/ Integer/SIZE 8) 64))
(def devicePtr (.allocate cuda memSize))

(def originalData (make-array (. Integer TYPE) 64))

(dotimes [i 64]
  (aset originalData i i))

(dotimes [i 64]
  (println (aget originalData i)))

(.copy cuda devicePtr originalData memSize)
; function parameters                                                                          
; first is pointer to device memory                                                            
(def pointer 0)
(.setParameter cuda 0 devicePtr)
(def delta (int 13))
(def offset (+ pointer (. NativeUtils getPointerSize)))
(.setParameter cuda offset delta)
(.setParameterSize cuda (+ offset (/ (. Integer SIZE) 8)))
(.setFunctionBlockShape cuda 64 1 1)

(.launch cuda)
(.synchronizeStream cuda (CUstream. 0))
(.copy cuda originalData devicePtr memSize)

(dotimes [i 64]
  (println (- (aget originalData i) 13)))

(.free cuda devicePtr)

;; The file test_module.cu contains the following code:

;; extern "C" __global__ void Add(int *src, int delta)
;; {
;;         src[threadIdx.x] += delta;
;; }

;; It is compiled into the following cubin file by nvcc test_module.cu --cubin:

;; architecture {sm_10}
;; abiversion   {1}
;; modname      {cubin}
;; code {
;;         name = Add
;;         lmem = 0
;;         smem = 28
;;         reg  = 2
;;         bar  = 0
;;         const {
;;                         segname = const
;;                         segnum  = 1
;;                         offset  = 0
;;                         bytes   = 4
;;                 mem {
;;                         0x00000004 
;;                 }
;;         }
;;         bincode {
;;                 0x1000c805 0x0423c780 0x60040005 0x00000003 
;;                 0xd00e0201 0x80c00780 0x2000cc01 0x04200780 
;;                 0xd00e0201 0xa0c00781 
;;         }
;; }

;; According to lscpi the card is called GeForce GTX 280. 
