(define NumWorkers (int-top))
(define NumMsgsPerWorker (int-top))
(define WritePercent (modulo (int-top) 100))

(define (build-vector n f)
  (letrec ((v (make-vector n #f))
           (loop (lambda (i)
                   (if (< i n)
                       (begin
                         (vector-set! v i (f i))
                         (loop (+ i 1)))
                       v))))
    (loop 0)))
(define (vector-foreach f v)
  (letrec ((loop (lambda (i)
                   (if (< i (vector-length v))
                       (begin
                         (f (vector-ref v i))
                         (loop (+ i 1)))
                       'done))))
    (loop 0)))

(define master (a/actor "master" (workers dictionary terminated)
                        (create-workers ()
                                        (let ((workers (build-vector NumWorkers (lambda (i) (a/create worker self dictionary i 0)))))
                                          (vector-foreach (lambda (w) (a/send w do-work)) workers)
                                          (a/become master workers dictionary terminated)))
                        (end-work ()
                                  (if (= (+ terminated 1) NumWorkers)
                                      (begin
                                        (a/send dictionary end-work)
                                        (a/terminate))
                                      (a/become master workers dictionary (+ terminated 1))))))
(define worker (a/actor "worker" (master dictionary id message-count)
                        (do-work ()
                                 (let ((an-int (random 100)))
                                   (if (< an-int WritePercent)
                                       (a/send dictionary write self (random 100) (random 100))
                                       (a/send dictionary read self (random 100)))
                                   (a/become worker master dictionary id (+ message-count 1))))
                        (result (value)
                             (if (<= (+ message-count 1) NumMsgsPerWorker)
                                 (let ((an-int (random 100)))
                                   (if (< an-int WritePercent)
                                       (a/send dictionary write self (random 100) (random 100))
                                       (a/send dictionary read self (random 100)))
                                   (a/become worker master dictionary id (+ message-count 1)))
                                 (begin
                                   (a/send master end-work)
                                   (a/terminate))))))
(define dictionary (a/actor "dictionary" (state)
                            (write (sender key value)
                                   (a/send sender result value)
                                   (a/become dictionary (cons (cons key (cons value '())) state)))
                            (read (sender key)
                                  (let ((res (assoc key state)))
                                    (if (pair? res)
                                        (a/send sender result (cdr res))
                                        (a/send sender result 0)))
                                  (a/become dictionary state))
                            (end-work () (a/terminate))))
(define dictionary-initial-state '())
(define (start-master)
  (let* ((dictionary (a/create dictionary dictionary-initial-state))
         (master (a/create master #f dictionary 0)))
    (a/send master create-workers)))
(start-master)
