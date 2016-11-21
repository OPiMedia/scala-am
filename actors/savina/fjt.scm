;; Adapted from Savina benchmarks ("Fork Join (throughput)" benchmark, coming from JGF)
(letrec ((N 10)
         (perform-computation (lambda (theta)
                                (let ((sint (+ 1 theta)))
                                  (* sint sint))))
         (throughput-actor
          (actor "throughput" (processed)
                 (message ()
                          (perform-computation 37.2)
                          (if (= (+ processed 1) N)
                              (terminate)
                              (become throughput-actor (+ processed 1))))))
         (actors (vector (create throughput-actor 0)
                         (create throughput-actor 0)
                         (create throughput-actor 0)
                         (create throughput-actor 0)))
         (vector-foreach (lambda (f v)
                           (letrec ((loop (lambda (i)
                                            (if (< i (vector-length v))
                                                (begin
                                                  (f (vector-ref v i))
                                                  (loop (+ i 1)))
                                                'done))))
                             (loop 0))))
         (loop (lambda (n)
                 (if (= n N)
                     'done
                     (begin
                       (vector-foreach (lambda (a) (send a message)) actors)
                       (loop (+ n 1)))))))
  (loop 0))
