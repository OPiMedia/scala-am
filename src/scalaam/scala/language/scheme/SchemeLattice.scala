package scalaam.language.scheme

import scalaam.core._

/** A lattice for Scheme should support the following operations */
trait SchemeLattice[L, E <: Exp, A <: Address] extends Lattice[L] {

  /** Can this value be considered true for conditionals? */
  def isTrue(x: L): Boolean

  /** Can this value be considered false for conditionals? */
  def isFalse(x: L): Boolean

  /** Performs a unary operation on the abstract value x */
  def unaryOp(op: SchemeOps.UnaryOperator)(x: L): MayFail[L, Error]

  /** Performs a binary operation on abstract values x and y */
  def binaryOp(op: SchemeOps.BinaryOperator)(x: L, y: L): MayFail[L, Error]

  /** Conjunction */
  def and(x: L, y: => L): L = (isTrue(x), isFalse(x)) match {
    case (true, false)  => y /* x is true: return y */
    case (false, true)  => bool(false) /* x is false: return false */
    case (false, false) => bottom /* x is not true nor false, it is therefore bottom */
    case (true, true)   => join(y, bool(false)) /* either x is true, and we have y, or x is false, and we have false */
  }

  /** Disjunction */
  def or(x: L, y: => L): L = (isTrue(x), isFalse(x)) match {
    case (true, false)  => x /* x is true, return x */
    case (false, true)  => y /* x is false, return y */
    case (false, false) => bottom /* x is not true nor false, it is therefore bottom */
    case (true, true)   => join(x, y) /* either x is true, and we have x, or x is false, and we have y */
  }

  /** The representation of a closure */
  type Closure = (E, Environment[A])

  /** Extract closures contained in this value */
  def getClosures(x: L): Set[Closure]

  /** Extract primitives contained in this value
    *  TODO[medium] find a way not to have a type parameter here */
  def getPrimitives[Primitive](x: L): Set[Primitive]

  def getPointerAddresses(x: L): Set[A]

  /** Injection of an integer */
  def number(x: Int): L

  /** Injection of a float */
  def real(x: Double): L

  /** Injection of a string */
  def string(x: String): L

  /** Injection of a boolean */
  def bool(x: Boolean): L

  /** Injection of a character */
  def char(x: Char): L

  /** Injection of a primitive function */
  def primitive[Primitive](x: Primitive): L

  /** Injection of a closure */
  def closure(x: Closure): L

  /** Injection of a symbol */
  def symbol(x: String): L

  /** Injection of a cons cell */
  def cons(car: L, cdr: L): L

  /** Injection of the nil value */
  def nil: L

  /** Injection of a pointer (to a cons cell, vector, etc.) */
  def pointer(a: A): L

  /** Takes the car of a cons cell */
  def car(x: L): MayFail[L, Error]

  /** Takes the cdr of a cons cell */
  def cdr(x: L): MayFail[L, Error]

  /* TODO: reimplement vectors
  /** Get a value from a vector. Returns the addresses where to look for the values */
  def vectorRef[Addr : Address](vector: L, index: L): MayFail[Set[Addr]]
  /** Changes a value inside a vector. The address given is an address where the
   * value can be stored if needed. Returns the vector value, as well as the
   * addresses to update in the store. The value stored is not passed to
   * vectorSet, but will be stored in the returned addresses. */
  def vectorSet[Addr : Address](vector: L, index: L, addr: Addr): MayFail[(L, Set[Addr])]
  /** Extract vector addresses contained in this value */
  def getVectors[Addr : Address](x: L): Set[Addr]
  /** Creates a vector of the given size, where the initial value lies at
   * address. Return the vector address wrapped in a lattice value, as well as
   * the vector value itsel */
  def vector[Addr : Address](addr: Addr, size: L, init: Addr): MayFail[(L, L)]
   */

  /* TODO: move this to the tests
  trait SchemeLatticeLaw extends MonoidLaw {
    import scalaz.std.boolean.conditional
    def bottomSubsumesItself: Boolean = subsumes(bottom, bottom)
    def bottomAlwaysSubsumed(x: L): Boolean = subsumes(x, bottom) && conditional(x != bottom, !subsumes(bottom, x))
    def joinedWithSubsumesRemainsEqual(x: L, y: L): Boolean = conditional(subsumes(x, y), join(x, y) == x)
    def joinedWithBottomRemainsEqual(x: L): Boolean = join(x, bottom) == x
    def joinedSubsumes(x: L, y: L): Boolean = {
      val xy = join(x, y)
      subsumes(xy, x) && subsumes(xy, y)
    }
    def joinedSubsumes3(x: L, y: L, z: L): Boolean = {
      /* Due to a bug detected on commit 7546a519, where {#t, Str, Int} did not subsume Str */
      val xyz = join(x, join(y, z))
      subsumes(xyz, x) && subsumes(xyz, y) && subsumes(xyz, z)
    }
    def injectBoolPreservesTruth: Boolean = isTrue(inject(true)) && isFalse(inject(false))
    def bottomNeitherTrueNorFalse: Boolean = !isTrue(bottom) && !isFalse(bottom)
    def boolTopIsTrueAndFalse: Boolean = {
      val boolTop = join(inject(true), inject(false))
      isTrue(boolTop) && isFalse(boolTop)
    }
    def unaryOpPreservesBottom(op: SchemeOps.UnaryOperator): Boolean =
      unaryOp(op)(bottom).extract == Some(bottom)
    def binaryOpPreservesBottom(op: SchemeOps.BinaryOperator, v: L): Boolean = {
      binaryOp(op)(bottom, bottom).extract == Some(bottom) &&
      binaryOp(op)(bottom, v).extract == Some(bottom) &&
      binaryOp(op)(v, bottom).extract == Some(bottom)
    }
    def notIsCorrect: Boolean = {
      unaryOp(UnaryOperator.Not)(inject(true)).map(isFalse).extract == Some(true) &&
      unaryOp(UnaryOperator.Not)(inject(false)).map(isTrue).extract == Some(true)
    }
    def andIsCorrect(b1: Boolean, b2: Boolean): Boolean = {
      val v1 = inject(b1)
      val v2 = inject(b2)
      if (b1 && b2) isTrue(and(v1, v2)) else isFalse(and(v1, v2))
    }
    def orIsCorrect(b1: Boolean, b2: Boolean): Boolean = {
      val v1 = inject(b1)
      val v2 = inject(b2)
      if (b1 || b2) isTrue(or(v1, v2)) else isFalse(or(v1, v2))
    }
    def ltIsCorrect(n1: Int, n2: Int): Boolean = {
      val v1 = inject(n1)
      val v2 = inject(n2)
      conditional(n1 < n2,
        (binaryOp(BinaryOperator.Lt)(v1, v2).extract.map(isTrue).getOrElse(false)) &&
          (binaryOp(BinaryOperator.Lt)(v2, v1).extract.map(isFalse).getOrElse(false)))
    }
    def eqIsCorrect(n1: Int, n2: Int): Boolean = {
      val v1 = inject(n1)
      val v2 = inject(n2)
      if (n1 == n2) {
        (binaryOp(BinaryOperator.Eq)(v1, v2).extract.map(isTrue).getOrElse(false) &&
          binaryOp(BinaryOperator.Eq)(v2, v1).extract.map(isTrue).getOrElse(false))
      } else {
        (binaryOp(BinaryOperator.Eq)(v1, v2).extract.map(isFalse).getOrElse(false) &&
          binaryOp(BinaryOperator.Eq)(v2, v1).extract.map(isFalse).getOrElse(false))
      }
    }
    /* TODO: more properties */
  }
 */
}

object SchemeLattice {
  def apply[L, E <: Exp, A <: Address](
      implicit lat: SchemeLattice[L, E, A]): SchemeLattice[L, E, A] = lat
}
