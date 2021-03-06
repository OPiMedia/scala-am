package scalaam.language.lambda

import scalaam.core._

trait LambdaLattice[L, A <: Address] extends Lattice[L] {
  def function(e: LambdaExp, env: Environment[A]): L
  def closures(f: L): Set[(LambdaExp, Environment[A])]
}

object LambdaLattice {
  def apply[L, A <: Address]()(implicit l: LambdaLattice[L, A]): LambdaLattice[L, A] = l
}

case class LambdaSetLattice[A <: Address]() {
  case class L(vals: Set[(LambdaExp, Environment[A])])

  object L {
    implicit val typeclass = new LambdaLattice[L, A] {
      def show(x: L)                                  = "{" ++ x.vals.mkString(",") ++ "}"
      def function(e: LambdaExp, env: Environment[A]) = L(Set((e, env)))
      def closures(f: L)                              = f.vals

      def bottom                  = L(Set.empty)
      def top                     = throw LatticeTopUndefined
      def join(x: L, y: => L)     = L(x.vals.union(y.vals))
      def subsumes(x: L, y: => L) = y.vals.subsetOf(x.vals)

      import scalaam.lattice.BoolLattice
      def eql[B: BoolLattice](x: L, y: L): B = BoolLattice[B].inject(y.vals.subsetOf(x.vals))
    }
  }
}
