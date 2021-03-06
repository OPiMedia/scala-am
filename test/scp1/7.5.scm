(define (atom? x)
  (not (pair? x)))

(define (same-structure? l1 l2)
  (cond ((and (atom? l1) (atom? l2)) #t)
        ((or  (atom? l1) (atom? l2)) #f)
        (else (and (same-structure? (car l1) (car l2))
                   (same-structure? (cdr l1) (cdr l2))))))

(define (same-structure?-or l1 l2)
  (or (and (atom? l1) (atom? l2))
      (and (pair? l1)
           (pair? l2)
           (same-structure?-or (car l1) (car l2))
           (same-structure?-or (cdr l1) (cdr l2)))))

(and (same-structure? '((1 2) ((3 . 4) ((5 6) ((7 8) (9)))))
                   '((a b) ((c . d) ((e f) ((g h) (i))))))
     (not (same-structure? '((1 2) ((3 4) ((5 6) ((7 8) (9)))))
                   '((((1 2) (3 4)) ((5 6) (7 8))) 9))))