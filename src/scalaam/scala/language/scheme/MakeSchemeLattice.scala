package scalaam.language.scheme

import scalaam.core._
import scalaam.lattice._
import scalaam.util.{Monoid, MonoidInstances}

import SchemeOps._
import UnaryOperator._
import BinaryOperator._

/** Defines a Scheme lattice based on other lattices */
/** TODO[medium]: use Show and ShowStore here */
class MakeSchemeLattice[
    E <: Exp,
    A <: Address,
    S: StringLattice,
    B: BoolLattice,
    I: IntLattice,
    R: RealLattice,
    C: CharLattice,
    Sym: SymbolLattice
] {
  case class TypeError(message: String, on: Value)                           extends Error
  case class OperatorNotApplicable(operator: String, arguments: List[Value]) extends Error

  /** We first implement all possible operations on single values, that can be
    * only joined when compatible. This therefore is not a lattice but will be
    * used to build the set lattice */
  sealed trait Value extends SmartHash
  case object Bot extends Value {
    override def toString = "⊥"
  }
  case class Str(s: S) extends Value {
    override def toString = StringLattice[S].show(s)
  }
  case class Bool(b: B) extends Value {
    override def toString = BoolLattice[B].show(b)
  }
  case class Int(i: I) extends Value {
    override def toString = IntLattice[I].show(i)
  }
  case class Real(r: R) extends Value {
    override def toString = RealLattice[R].show(r)
  }
  case class Char(c: C) extends Value {
    override def toString = CharLattice[C].show(c)
  }
  case class Symbol(s: Sym) extends Value {
    override def toString = SymbolLattice[Sym].show(s)
  }

  /** TODO[medium] find a way not to have a type parameter here */
  case class Prim[Primitive](prim: Primitive) extends Value {
    override def toString = s"#prim"
  }
  case class Clo(lambda: E, env: Environment[A]) extends Value {
    override def toString = "#clo"
  }

  case class Cons(car: L, cdr: L) extends Value {
    override def toString = s"($car . $cdr)"
  }
  case object Nil extends Value {
    override def toString = "()"
  }

  case class Pointer(a: A) extends Value {
    override def toString = s"#pointer($a)"
  }
  /*
  case class Vec[Addr : Address](size: I, elements: Map[I, Addr], init: Addr) extends Value {
    override def toString = {
      val els = elements.toList.map({
        case (k, v) => s"${IntLattice[I].shows(k)}: $v"
      }).mkString(", ")
      s"Vec(${IntLattice[I].shows(size)}, {$els}, $init)"
    }
  }
  case class VectorAddress[Addr : Address](a: Addr) extends Value
   */

  /** The injected true value */
  val True = Bool(BoolLattice[B].inject(true))

  /** The injected false value */
  val False = Bool(BoolLattice[B].inject(false))

  object Value {

    /** The bottom value */
    def bottom = Bot

    /** Check if two values are compatible to be joined */
    def compatible(x: Value, y: => Value): Boolean = (x, y) match {
      case (Bot, _) | (_, Bot) => true
      case (_: Str, _: Str)    => true
      case (_: Bool, _: Bool)  => true
      case (_: Int, _: Int)    => true
      case (_: Real, _: Real)  => true
      case (_: Char, _: Char)  => true
      case _                   => false
    }

    /** Tries to join. Returns a Right element if it can, otherwise a Left. */
    def join(x: Value, y: => Value): Either[(Value, Value), Value] = if (x == y) { Right(x) } else {
      (x, y) match {
        case (Bot, _)             => Right(y)
        case (_, Bot)             => Right(x)
        case (Str(s1), Str(s2))   => Right(Str(StringLattice[S].join(s1, s2)))
        case (Bool(b1), Bool(b2)) => Right(Bool(BoolLattice[B].join(b1, b2)))
        case (Int(i1), Int(i2))   => Right(Int(IntLattice[I].join(i1, i2)))
        case (Real(f1), Real(f2)) => Right(Real(RealLattice[R].join(f1, f2)))
        case (Char(c1), Char(c2)) => Right(Char(CharLattice[C].join(c1, c2)))
        case _                    => Left((x, y))
      }
    }
    def subsumes(x: Value, y: => Value): Boolean = if (x == y) { true } else {
      (x, y) match {
        case (_, Bot)             => true
        case (Str(s1), Str(s2))   => StringLattice[S].subsumes(s1, s2)
        case (Bool(b1), Bool(b2)) => BoolLattice[B].subsumes(b1, b2)
        case (Int(i1), Int(i2))   => IntLattice[I].subsumes(i1, i2)
        case (Real(f1), Real(f2)) => RealLattice[R].subsumes(f1, f2)
        case (Char(c1), Char(c2)) => CharLattice[C].subsumes(c1, c2)
        case _                    => false
      }
    }

    def isTrue(x: Value): Boolean = x match {
      case Bool(b) => BoolLattice[B].isTrue(b)
      case Bot     => false
      case _       => true
    }
    def isFalse(x: Value): Boolean = x match {
      case Bool(b) => BoolLattice[B].isFalse(b)
      case Bot     => false
      case _       => false
    }

//    import scala.language.implicitConversions
//    implicit def mayFailSuccess(l: Value): MayFail[Value, Error] = MayFail.success(l)
//    implicit def mayFailError(err: Error): MayFail[Value, Error] = MayFail.failure(err)
    def unaryOp(op: UnaryOperator)(x: Value): MayFail[Value, Error] =
      if (x == Bot) { MayFail.success(Bot) } else {
        op match {
          case IsNull =>
            MayFail.success(x match {
              case Nil => True
              case _   => False
            })
          case IsCons =>
            MayFail.success(x match {
              case Cons(_, _) => True
              case _          => False
            })
          case IsPointer =>
            MayFail.success(x match {
              case Pointer(_) => True
              case _          => False
            })
          case IsChar =>
            MayFail.success(x match {
              case Char(_) => True
              case _       => False
            })
          case IsSymbol =>
            MayFail.success(x match {
              case Symbol(_) => True
              case _         => False
            })
          case IsString =>
            MayFail.success(x match {
              case Str(_) => True
              case _      => False
            })
          case IsInteger =>
            MayFail.success(x match {
              case Int(_) => True
              case _      => False
            })
          case IsReal =>
            MayFail.success(x match {
              case Real(_) => True
              case _       => False
            })
          case IsBoolean =>
            MayFail.success(x match {
              case Bool(_) => True
              case _       => False
            })
          case IsVector =>
            MayFail.success(x match {
              //        case Vec(_, _, _) => True
              //        case VectorAddress(_) => True
              case _ => False
            })
          case Not =>
            MayFail.success(x match {
              case Bool(b) => Bool(BoolLattice[B].not(b))
              case _       => False /* any value is true */
            })
          case Ceiling =>
            x match {
              case Int(n)  => MayFail.success(Int(n))
              case Real(n) => MayFail.success(Real(RealLattice[R].ceiling(n)))
              case _       => MayFail.failure(OperatorNotApplicable("ceiling", List(x)))
            }
          case Floor =>
            x match {
              case Int(n)  => MayFail.success(Int(n))
              case Real(n) => MayFail.success(Real(RealLattice[R].floor(n)))
              case _       => MayFail.failure(OperatorNotApplicable("floor", List(x)))
            }
          case Round =>
            x match {
              case Int(n)  => MayFail.success(Int(n))
              case Real(n) => MayFail.success(Real(RealLattice[R].round(n)))
              case _       => MayFail.failure(OperatorNotApplicable("round", List(x)))
            }
          case Log =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].log(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].log(n)))
              case _       => MayFail.failure(OperatorNotApplicable("log", List(x)))
            }
          case Random =>
            x match {
              case Int(n)  => MayFail.success(Int(IntLattice[I].random(n)))
              case Real(n) => MayFail.success(Real(RealLattice[R].random(n)))
              case _       => MayFail.failure(OperatorNotApplicable("random", List(x)))
            }
          case Sin =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].sin(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].sin(n)))
              case _       => MayFail.failure(OperatorNotApplicable("sin", List(x)))
            }
          case ASin =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].asin(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].asin(n)))
              case _       => MayFail.failure(OperatorNotApplicable("asin", List(x)))
            }
          case Cos =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].cos(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].cos(n)))
              case _       => MayFail.failure(OperatorNotApplicable("cos", List(x)))
            }
          case ACos =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].acos(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].acos(n)))
              case _       => MayFail.failure(OperatorNotApplicable("acos", List(x)))
            }
          case Tan =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].tan(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].tan(n)))
              case _       => MayFail.failure(OperatorNotApplicable("tan", List(x)))
            }
          case ATan =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].atan(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].atan(n)))
              case _       => MayFail.failure(OperatorNotApplicable("atan", List(x)))
            }
          case Sqrt =>
            x match {
              case Int(n)  => MayFail.success(Real(RealLattice[R].sqrt(IntLattice[I].toReal(n))))
              case Real(n) => MayFail.success(Real(RealLattice[R].sqrt(n)))
              case _       => MayFail.failure(OperatorNotApplicable("sqrt", List(x)))
            }
          case VectorLength =>
            x match {
              //        case Vec(size, _, _) => Int(size)
              case _ => MayFail.failure(OperatorNotApplicable("vector-length", List(x)))
            }
          case StringLength =>
            x match {
              case Str(s) => MayFail.success(Int(StringLattice[S].length(s)))
              case _      => MayFail.failure(OperatorNotApplicable("string-length", List(x)))
            }
          case NumberToString =>
            x match {
              case Int(n)  => MayFail.success(Str(IntLattice[I].toString(n)))
              case Real(n) => MayFail.success(Str(RealLattice[R].toString(n)))
              case _       => MayFail.failure(OperatorNotApplicable("number->string", List(x)))
            }
          case SymbolToString =>
            x match {
              case Symbol(s) => MayFail.success(Str(SymbolLattice[Sym].toString(s)))
              case _         => MayFail.failure(OperatorNotApplicable("symbol->string", List(x)))
            }
          case StringToSymbol =>
            x match {
              case Str(s) => MayFail.success(Symbol(StringLattice[S].toSymbol(s)))
              case _      => MayFail.failure(OperatorNotApplicable("string->symbol", List(x)))
            }
          case ExactToInexact =>
            x match {
              case Int(n)  => MayFail.success(Real(IntLattice[I].toReal(n)))
              case Real(n) => MayFail.success(Real(n))
              case _       => MayFail.failure(OperatorNotApplicable("exact->inexact", List(x)))
            }
          case InexactToExact =>
            x match {
              case Int(n)  => MayFail.success(Int(n))
              case Real(n) => MayFail.success(Int(RealLattice[R].toInt[I](n))) /* should introduce fractions */
              case _       => MayFail.failure(OperatorNotApplicable("inexact->exact", List(x)))
            }
        }
      }

    def binaryOp(op: BinaryOperator)(x: Value, y: Value): MayFail[Value, Error] =
      if (x == Bot || y == Bot) { MayFail.success(Bot) } else {
        op match {
          case Plus =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Int(IntLattice[I].plus(n1, n2)))
              case (Int(n1), Real(n2)) =>
                MayFail.success(Real(RealLattice[R].plus(IntLattice[I].toReal(n1), n2)))
              case (Real(n1), Int(n2)) =>
                MayFail.success(Real(RealLattice[R].plus(n1, IntLattice[I].toReal(n2))))
              case (Real(n1), Real(n2)) => MayFail.success(Real(RealLattice[R].plus(n1, n2)))
              case _                    => MayFail.failure(OperatorNotApplicable("+", List(x, y)))
            }
          case Minus =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Int(IntLattice[I].minus(n1, n2)))
              case (Int(n1), Real(n2)) =>
                MayFail.success(Real(RealLattice[R].minus(IntLattice[I].toReal(n1), n2)))
              case (Real(n1), Int(n2)) =>
                MayFail.success(Real(RealLattice[R].minus(n1, IntLattice[I].toReal(n2))))
              case (Real(n1), Real(n2)) => MayFail.success(Real(RealLattice[R].minus(n1, n2)))
              case _                    => MayFail.failure(OperatorNotApplicable("-", List(x, y)))
            }
          case Times =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Int(IntLattice[I].times(n1, n2)))
              case (Int(n1), Real(n2)) =>
                MayFail.success(Real(RealLattice[R].times(IntLattice[I].toReal(n1), n2)))
              case (Real(n1), Int(n2)) =>
                MayFail.success(Real(RealLattice[R].times(n1, IntLattice[I].toReal(n2))))
              case (Real(n1), Real(n2)) => MayFail.success(Real(RealLattice[R].times(n1, n2)))
              case _                    => MayFail.failure(OperatorNotApplicable("*", List(x, y)))
            }
          case Quotient =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Int(IntLattice[I].quotient(n1, n2)))
              case _                  => MayFail.failure(OperatorNotApplicable("quotient", List(x, y)))
            }
          case Div =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Real(IntLattice[I].div[R](n1, n2)))
              case (Int(n1), Real(n2)) =>
                MayFail.success(Real(RealLattice[R].div(IntLattice[I].toReal(n1), n2)))
              case (Real(n1), Int(n2)) =>
                MayFail.success(Real(RealLattice[R].div(n1, IntLattice[I].toReal(n2))))
              case (Real(n1), Real(n2)) => MayFail.success(Real(RealLattice[R].div(n1, n2)))
              case _                    => MayFail.failure(OperatorNotApplicable("/", List(x, y)))
            }
          case Modulo =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Int(IntLattice[I].modulo(n1, n2)))
              case _                  => MayFail.failure(OperatorNotApplicable("modulo", List(x, y)))
            }
          case Remainder =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Int(IntLattice[I].remainder(n1, n2)))
              case _                  => MayFail.failure(OperatorNotApplicable("modulo", List(x, y)))
            }
          case Lt =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Bool(IntLattice[I].lt(n1, n2)))
              case (Int(n1), Real(n2)) =>
                MayFail.success(Bool(RealLattice[R].lt(IntLattice[I].toReal(n1), n2)))
              case (Real(n1), Int(n2)) =>
                MayFail.success(Bool(RealLattice[R].lt(n1, IntLattice[I].toReal(n2))))
              case (Real(n1), Real(n2)) => MayFail.success(Bool(RealLattice[R].lt(n1, n2)))
              case _                    => MayFail.failure(OperatorNotApplicable("<", List(x, y)))
            }
          case NumEq =>
            (x, y) match {
              case (Int(n1), Int(n2)) => MayFail.success(Bool(IntLattice[I].eql(n1, n2)))
              case (Int(n1), Real(n2)) =>
                MayFail.success(Bool(RealLattice[R].eql(IntLattice[I].toReal(n1), n2)))
              case (Real(n1), Int(n2)) =>
                MayFail.success(Bool(RealLattice[R].eql(n1, IntLattice[I].toReal(n2))))
              case (Real(n1), Real(n2)) => MayFail.success(Bool(RealLattice[R].eql(n1, n2)))
              case _                    => MayFail.failure(OperatorNotApplicable("number=", List(x, y)))
            }
          case Eq =>
            MayFail.success((x, y) match {
              case (Str(s1), Str(s2))       => Bool(StringLattice[S].eql(s1, s2)) /* TODO: this isn't really physical equality for strings */
              case (Bool(b1), Bool(b2))     => Bool(BoolLattice[B].eql(b1, b2))
              case (Int(n1), Int(n2))       => Bool(IntLattice[I].eql(n1, n2))
              case (Real(n1), Real(n2))     => Bool(RealLattice[R].eql(n1, n2))
              case (Char(c1), Char(c2))     => Bool(CharLattice[C].eql(c1, c2))
              case (Symbol(s1), Symbol(s2)) => Bool(SymbolLattice[Sym].eql(s1, s2))
              case (Nil, Nil)               => True
              case (Prim(_), Prim(_))       => Bool(BoolLattice[B].inject(x == y))
              case (Clo(_, _), Clo(_, _))   => Bool(BoolLattice[B].inject(x == y))
              case (Cons(_, _), Cons(_, _)) => Bool(BoolLattice[B].inject(x == y))
              //          case (VectorAddress(_), VectorAddress(_)) => Bool(BoolLattice[B].inject(x == y))
              case _ => False
            })
          case StringAppend =>
            (x, y) match {
              case (Str(s1), Str(s2)) => MayFail.success(Str(StringLattice[S].append(s1, s2)))
              case _                  => MayFail.failure(OperatorNotApplicable("string-append", List(x, y)))
            }
          case StringRef =>
            (x, y) match {
              case (Str(s), Int(n)) => MayFail.success(Char(StringLattice[S].ref(s, n)))
              case _                => MayFail.failure(OperatorNotApplicable("string-ref", List(x, y)))
            }
          case StringLt =>
            (x, y) match {
              case (Str(s1), Str(s2)) => MayFail.success(Bool(StringLattice[S].lt(s1, s2)))
              case _                  => MayFail.failure(OperatorNotApplicable("string<?", List(x, y)))
            }
        }
      }

    def number(x: scala.Int): Value               = Int(IntLattice[I].inject(x))
    def real(x: Double): Value                    = Real(RealLattice[R].inject(x))
    def string(x: String): Value                  = Str(StringLattice[S].inject(x))
    def bool(x: Boolean): Value                   = Bool(BoolLattice[B].inject(x))
    def char(x: scala.Char): Value                = Char(CharLattice[C].inject(x))
    def primitive[Primitive](x: Primitive): Value = Prim(x)
    def closure(x: schemeLattice.Closure): Value  = Clo(x._1, x._2.restrictTo(x._1.fv))
    def symbol(x: String): Value                  = Symbol(SymbolLattice[Sym].inject(x))
    def nil: Value                                = Nil
    def cons(car: L, cdr: L): Value               = Cons(car, cdr)
    def pointer(a: A): Value                      = Pointer(a)

    def getClosures(x: Value): Set[schemeLattice.Closure] = x match {
      case Clo(lam, env) => Set((lam, env))
      case _             => Set()
    }
    def getPrimitives[Primitive](x: Value): Set[Primitive] = x match {
      case Prim(p: Primitive @unchecked) => Set(p)
      case _                             => Set()
    }
    def getPointerAddresses(x: Value): Set[A] = x match {
      case Pointer(a) => Set(a)
      case _          => Set()
    }

    def car(x: Value): MayFail[L, Error] = x match {
      case Cons(car, _) => MayFail.success(car)
      case _            => MayFail.failure(TypeError("expecting cons to access car", x))
    }

    def cdr(x: Value): MayFail[L, Error] = x match {
      case Cons(_, cdr) => MayFail.success(cdr)
      case _            => MayFail.failure(TypeError("expecting cons to access cdr", x))
    }
    /*
    def vectorRef[Addr : Address](vector: Value, index: Value): MayFail[Set[Addr]] = (vector, index) match {
      case (Vec(size, content: Map[I, Addr] @unchecked, init: Addr @unchecked), Int(index)) => {
        val comp = IntLattice[I].lt(index, size)
        val t: Set[Addr] = if (BoolLattice[B].isTrue(comp)) {
          val vals = content.filterKeys(index2 => BoolLattice[B].isTrue(IntLattice[I].eql(index, index2))).values
          /* init doesn't have to be included if we know for sure that index is precise enough */
          vals.foldLeft(Set(init))((acc, v) => acc + v)
        } else { Set() }
        /* Don't perform bound checks here because we would get too many spurious flows */
        val f: Set[Addr] = Set()
        MayFailSuccess(t ++ f)
      }
      case (_: Vec[Addr] @unchecked, _) => MayFailError(List(OperatorNotApplicable("vector-ref", List(vector, index))))
      case _ => MayFailError(List(OperatorNotApplicable("vector-ref", List(vector, index))))
    }

    def vectorSet[Addr : Address](vector: Value, index: Value, addr: Addr): MayFail[(Value, Set[Addr])] = (vector, index) match {
      case (Vec(size, content: Map[I, Addr] @unchecked, init: Addr @unchecked), Int(index)) => {
        val comp = IntLattice[I].lt(index, size)
        val t: (Value, Set[Addr]) = if (BoolLattice[B].isTrue(comp)) {
          content.get(index) match {
            case Some(a: Addr @unchecked) => (vector, Set(a))
            case None => (Vec(size, content + (index -> addr), init), Set(addr))
          }
        } else { (Bot, Set()) }
        val f: (Value, Set[Addr]) = (Bot, Set())
        MayFailSuccess((join(t._1, f._1), t._2 ++ f._2))
      }
      case (_: Vec[Addr] @unchecked, _) => MayFailError(List(OperatorNotApplicable("vector-set!", List(vector, index, addr))))
      case _ => MayFailError(List(OperatorNotApplicable("vector-set!", List(vector, index, addr))))
    }

/*    def getVectors[Addr : Address](x: Value) = x match {
      case VectorAddress(a: Addr @unchecked) => Set(a)
      case _ => Set()
    }

    def vector[Addr : Address](addr: Addr, size: Value, init: Addr): MayFail[(Value, Value)] = size match {
      case Int(size) => MayFailSuccess((VectorAddress(addr), Vec(size, Map[I, Addr](), init)))
      case _ => MayFailError(List(OperatorNotApplicable("vector", List(addr, size, init))))
    }
   */
   */
  }

  sealed trait L extends SmartHash {
    def foldMapL[X](f: Value => X)(implicit monoid: Monoid[X]): X
  }
  case class Element(v: Value) extends L {
    override def toString                                         = v.toString
    def foldMapL[X](f: Value => X)(implicit monoid: Monoid[X]): X = f(v)
  }
  case class Elements(vs: Set[Value]) extends L {
    override def toString = "{" + vs.mkString(",") + "}"
    def foldMapL[X](f: Value => X)(implicit monoid: Monoid[X]): X =
      vs.foldLeft(monoid.zero)((acc, x) => monoid.append(acc, f(x)))
  }

  import MonoidInstances.{boolOrMonoid, boolAndMonoid, setMonoid}
  implicit val lMonoid = new Monoid[L] {
    def append(x: L, y: => L): L = x match {
      case Element(Bot) => y
      case Element(a) =>
        y match {
          case Element(Bot) => x
          case Element(b) =>
            Value.join(a, b) match {
              case Left((v1, v2)) => Elements(Set(v1, v2))
              case Right(v)       => Element(v)
            }
          case _: Elements => append(Elements(Set(a)), y)
        }
      case Elements(as) =>
        y match {
          case Element(Bot) => x
          case Element(b)   => append(x, Elements(Set(b)))
          case Elements(bs) =>
            /* every element in the other set has to be joined in this set */
            Elements(as.foldLeft(bs)((acc, x2) =>
              if (acc.exists(x1 => Value.subsumes(x1, x2))) {
                /* the set already contains an element that subsumes x2, don't add it to the set */
                acc
              } else if (acc.exists(x1 => Value.compatible(x1, x2))) {
                /* merge x2 into another element of the set */
                acc.map(x1 =>
                  Value.join(x1, x2) match {
                    case Right(joined) => joined
                    case Left(_)       => x1
                })
              } else {
                /* just add x2 to the set */
                acc + x2
            }))
        }
    }
    def zero: L = Element(Bot)
  }
  implicit val lMFMonoid: Monoid[MayFail[L, Error]] = MonoidInstances.mayFail[L]

  val schemeLattice: SchemeLattice[L, E, A] = new SchemeLattice[L, E, A] {
    def show(x: L)             = x.toString /* TODO[easy]: implement better */
    def isTrue(x: L): Boolean  = x.foldMapL(Value.isTrue(_))(boolOrMonoid)
    def isFalse(x: L): Boolean = x.foldMapL(Value.isFalse(_))(boolOrMonoid)
    def unaryOp(op: UnaryOperator)(x: L): MayFail[L, Error] =
      x.foldMapL(x => Value.unaryOp(op)(x).map(x => Element(x)))
    def binaryOp(op: BinaryOperator)(x: L, y: L): MayFail[L, Error] =
      x.foldMapL(x => y.foldMapL(y => Value.binaryOp(op)(x, y).map(x => Element(x))))
    def join(x: L, y: => L): L = Monoid[L].append(x, y)
    def subsumes(x: L, y: => L): Boolean =
      y.foldMapL(
        y =>
          /* For every element in y, there exists an element of x that subsumes it */
          x.foldMapL(x => Value.subsumes(x, y))(boolOrMonoid))(boolAndMonoid)
    def car(x: L) = x.foldMapL(Value.car(_))
    def cdr(x: L) = x.foldMapL(Value.cdr(_))
    def top: L    = throw LatticeTopUndefined

    /*
    def vectorRef[Addr : Address](vector: L, index: L): MayFail[Set[Addr]] = foldMapL(vector, vector => foldMapL(index, index =>
      valueSchemeLattice.vectorRef(vector, index)))
    def vectorSet[Addr : Address](vector: L, index: L, addr: Addr): MayFail[(L, Set[Addr])] = foldMapL(vector, vector => foldMapL(index, index =>
      valueSchemeLattice.vectorSet(vector, index, addr).map({ case (v, addrs) => (wrap(v), addrs) })))
     */

    def getClosures(x: L): Set[Closure] = x.foldMapL(x => Value.getClosures(x))(setMonoid)
    def getPrimitives[Primitive](x: L): Set[Primitive] =
      x.foldMapL(x => Value.getPrimitives[Primitive](x))(setMonoid)
    def getPointerAddresses(x: L): Set[A] = x.foldMapL(x => Value.getPointerAddresses(x))(setMonoid)
//    def getVectors[Addr : Address](x: L): Set[Addr] = foldMapL(x, x => valueSchemeLattice.getVectors(x))

    def bottom: L                             = Element(Value.bottom)
    def number(x: scala.Int): L               = Element(Value.number(x))
    def real(x: Double): L                    = Element(Value.real(x))
    def string(x: String): L                  = Element(Value.string(x))
    def char(x: scala.Char): L                = Element(Value.char(x))
    def bool(x: Boolean): L                   = Element(Value.bool(x))
    def primitive[Primitive](x: Primitive): L = Element(Value.primitive[Primitive](x))
    def closure(x: Closure): L                = Element(Value.closure(x))
    def symbol(x: String): L                  = Element(Value.symbol(x))
    def cons(car: L, cdr: L): L               = Element(Value.cons(car, cdr))
    def pointer(a: A): L                      = Element(Value.pointer(a))
    /*
    def vector[Addr : Address](addr: Addr, size: L, init: Addr): MayFail[(L, L)] = foldMapL(size, size =>
      valueSchemeLattice.vector(addr, size, init).map({ case (a, v) => (Element(a), Element(v)) }))
     */
    def nil: L = Element(Value.nil)

    def eql[B2: BoolLattice](x: L, y: L): B2 = ??? // TODO[medium] implement
  }

  object L {
    implicit val lattice: SchemeLattice[L, E, A] = schemeLattice
  }
}
