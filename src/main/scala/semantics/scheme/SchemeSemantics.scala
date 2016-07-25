import SchemeOps._
import scalaz.Scalaz._
import scalaz._

/**
 * Basic Scheme semantics, without any optimization
 */
class BaseSchemeSemantics[Abs : IsSchemeLattice, Addr : Address, Time : Timestamp](primitives: Primitives[Addr, Abs])
    extends BaseSemantics[SchemeExp, Abs, Addr, Time] {
  def sabs = implicitly[IsSchemeLattice[Abs]]

  type Env = Environment[Addr]
  type Sto = Store[Addr, Abs]
  type Actions = Set[Action[SchemeExp, Abs, Addr]]

  trait SchemeFrame extends Frame {
    def subsumes(that: Frame) = that.equals(this)
    override def toString = s"${this.getClass.getSimpleName}"
  }
  case class FrameFuncallOperator(fexp: SchemeExp, args: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameFuncallOperands(f: Abs, fexp: SchemeExp, cur: SchemeExp, args: List[(SchemeExp, Abs)], toeval: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameIf(cons: SchemeExp, alt: SchemeExp, env: Env) extends SchemeFrame
  case class FrameLet(variable: String, bindings: List[(String, Abs)], toeval: List[(String, SchemeExp)], body: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameLetStar(variable: String, bindings: List[(String, SchemeExp)], body: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameLetrec(addr: Addr, bindings: List[(Addr, SchemeExp)], body: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameSet(variable: String, env: Env) extends SchemeFrame
  case class FrameBegin(rest: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameCond(cons: List[SchemeExp], clauses: List[(SchemeExp, List[SchemeExp])], env: Env) extends SchemeFrame
  case class FrameCase(clauses: List[(List[SchemeValue], List[SchemeExp])], default: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameAnd(rest: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameOr(rest: List[SchemeExp], env: Env) extends SchemeFrame
  case class FrameDefine(variable: String, env: Env) extends SchemeFrame

  protected def evalBody(body: List[SchemeExp], env: Env, store: Sto): Actions = body match {
    case Nil => Action.value(sabs.inject(false), store)
    case List(exp) => Action.eval(exp, env, store)
    case exp :: rest => Action.push(FrameBegin(rest, env), exp, env, store)
  }


  def conditional(v: Abs, t: => Actions, f: => Actions): Actions =
    (if (sabs.isTrue(v)) t else Action.none) ++ (if (sabs.isFalse(v)) f else Action.none)

  def evalCall(function: Abs, fexp: SchemeExp, argsv: List[(SchemeExp, Abs)], store: Sto, t: Time): Actions = {
    val fromClo: Actions = sabs.getClosures[SchemeExp, Addr](function).map({
      case (SchemeLambda(args, body, pos), env1) =>
        if (args.length == argsv.length) {
          bindArgs(args.zip(argsv), env1, store, t) match {
            case (env2, store) =>
              if (body.length == 1)
                Action.stepIn(fexp, (SchemeLambda(args, body, pos), env1), body.head, env2, store, argsv)
              else
                Action.stepIn(fexp, (SchemeLambda(args, body, pos), env1), SchemeBegin(body, pos), env2, store, argsv)
          }
        } else { Action.error(ArityError(fexp.toString, args.length, argsv.length)) }
      case (lambda, _) => Action.error(TypeError(lambda.toString, "operator", "closure", "not a closure"))
    })
    val fromPrim: Actions = sabs.getPrimitives(function).flatMap(prim =>
      for { (res, store2, effects) <- prim.call(fexp, argsv, store, t) } yield Action.value(res, store2, effects) )
    if (fromClo.isEmpty && fromPrim.isEmpty) {
      Action.error(TypeError(function.toString, "operator", "function", "not a function"))
    } else {
      fromClo ++ fromPrim
    }
  }

  protected def evalValue(v: Value): Option[Abs] = v match {
    case ValueString(s) => Some(sabs.inject(s))
    case ValueInteger(n) => Some(sabs.inject(n))
    case ValueFloat(n) => Some(sabs.inject(n))
    case ValueBoolean(b) => Some(sabs.inject(b))
    case _ => None
  }

  protected def funcallArgs(f: Abs, fexp: SchemeExp, args: List[(SchemeExp, Abs)], toeval: List[SchemeExp], env: Env, store: Sto, t: Time): Actions = toeval match {
    case Nil => evalCall(f, fexp, args.reverse, store, t)
    case e :: rest => Action.push(FrameFuncallOperands(f, fexp, e, args, rest, env), e, env, store)
  }
  protected def funcallArgs(f: Abs, fexp: SchemeExp, args: List[SchemeExp], env: Env, store: Sto, t: Time): Actions =
    funcallArgs(f, fexp, List(), args, env, store, t)

  protected def evalQuoted(exp: SExp, store: Sto, t: Time): (Abs, Sto) = exp match {
    case SExpIdentifier(sym, _) => (sabs.injectSymbol(sym), store)
    case SExpPair(car, cdr, _) => {
      val care: SchemeExp = SchemeIdentifier(car.toString, car.pos)
      val cdre: SchemeExp = SchemeIdentifier(cdr.toString, cdr.pos)
      val cara = addr.cell(care, t)
      val (carv, store2) = evalQuoted(car, store, t)
      val cdra = addr.cell(cdre, t)
      val (cdrv, store3) = evalQuoted(cdr, store2, t)
      (sabs.cons(cara, cdra), store3.extend(cara, carv).extend(cdra, cdrv))
    }
    case SExpValue(v, _) => (v match {
      case ValueString(str) => sabs.inject(str)
      case ValueCharacter(c) => sabs.inject(c)
      case ValueSymbol(sym) => sabs.injectSymbol(sym) /* shouldn't happen */
      case ValueInteger(n) => sabs.inject(n)
      case ValueFloat(n) => sabs.inject(n)
      case ValueBoolean(b) => sabs.inject(b)
      case ValueNil => sabs.nil
    }, store)
    case SExpQuoted(q, pos) => evalQuoted(SExpPair(SExpIdentifier("quote", pos), SExpPair(q, SExpValue(ValueNil, pos), pos), pos), store, t)
  }

  def stepEval(e: SchemeExp, env: Env, store: Sto, t: Time) = e match {
    case λ: SchemeLambda => Action.value(sabs.inject[SchemeExp, Addr]((λ, env)), store)
    case SchemeFuncall(f, args, _) => Action.push(FrameFuncallOperator(f, args, env), f, env, store)
    case SchemeIf(cond, cons, alt, _) => Action.push(FrameIf(cons, alt, env), cond, env, store)
    case SchemeLet(Nil, body, _) => evalBody(body, env, store)
    case SchemeLet((v, exp) :: bindings, body, _) => Action.push(FrameLet(v, List(), bindings, body, env), exp, env, store)
    case SchemeLetStar(Nil, body, _) => evalBody(body, env, store)
    case SchemeLetStar((v, exp) :: bindings, body, _) => Action.push(FrameLetStar(v, bindings, body, env), exp, env, store)
    case SchemeLetrec(Nil, body, _) => evalBody(body, env, store)
    case SchemeLetrec((v, exp) :: bindings, body, _) => {
      val variables = v :: bindings.map(_._1)
      val addresses = variables.map(v => addr.variable(v, abs.bottom, t))
      val (env1, store1) = variables.zip(addresses).foldLeft((env, store))({ case ((env, store), (v, a)) => (env.extend(v, a), store.extend(a, abs.bottom)) })
      Action.push(FrameLetrec(addresses.head, addresses.tail.zip(bindings.map(_._2)), body, env1), exp, env1, store1)
    }
    case SchemeSet(variable, exp, _) => Action.push(FrameSet(variable, env), exp, env, store)
    case SchemeBegin(body, _) => evalBody(body, env, store)
    case SchemeCond(Nil, _) => Action.error(NotSupported("cond without clauses"))
    case SchemeCond((cond, cons) :: clauses, _) => Action.push(FrameCond(cons, clauses, env), cond, env, store)
    case SchemeCase(key, clauses, default, _) => Action.push(FrameCase(clauses, default, env), key, env, store)
    case SchemeAnd(Nil, _) => Action.value(sabs.inject(true), store)
    case SchemeAnd(exp :: exps, _) => Action.push(FrameAnd(exps, env), exp, env, store)
    case SchemeOr(Nil, _) => Action.value(sabs.inject(false), store)
    case SchemeOr(exp :: exps, _) => Action.push(FrameOr(exps, env), exp, env, store)
    case SchemeDefineVariable(name, exp, _) => Action.push(FrameDefine(name, env), exp, env, store)
    case SchemeDefineFunction(name, args, body, pos) => {
      val a = addr.variable(name, abs.bottom, t)
      val v = sabs.inject[SchemeExp, Addr]((SchemeLambda(args, body, pos), env))
      val env1 = env.extend(name, a)
      val store1 = store.extend(a, v)
      Action.value(v, store)
    }
    case SchemeIdentifier(name, _) => env.lookup(name) match {
      case Some(a) => store.lookup(a) match {
        case Some(v) => Action.value(v, store, Set(EffectReadVariable(a)))
        case None => Action.error(UnboundAddress(a.toString))
      }
      case None => Action.error(UnboundVariable(name))
    }
    case SchemeQuoted(quoted, _) => evalQuoted(quoted, store, t) match {
      case (value, store2) => Action.value(value, store2)
    }
    case SchemeValue(v, _) => evalValue(v) match {
      case Some(v) => Action.value(v, store)
      case None => Action.error(NotSupported(s"Unhandled value: $v"))
    }
  }

  def stepKont(v: Abs, frame: Frame, store: Sto, t: Time) = frame match {
    case FrameFuncallOperator(fexp, args, env) => funcallArgs(v, fexp, args, env, store, t)
    case FrameFuncallOperands(f, fexp, exp, args, toeval, env) => funcallArgs(f, fexp, (exp, v) :: args, toeval, env, store, t)
    case FrameIf(cons, alt, env) =>
      conditional(v, Action.eval(cons, env, store), Action.eval(alt, env, store))
    case FrameLet(name, bindings, Nil, body, env) => {
      val variables = name :: bindings.reverse.map(_._1)
      val addresses = variables.map(variable => addr.variable(variable, v, t))
      val (env1, store1) = ((name, v) :: bindings).zip(addresses).foldLeft((env, store))({
        case ((env, store), ((variable, value), a)) => (env.extend(variable, a), store.extend(a, value))
      })
      evalBody(body, env1, store1)
    }
    case FrameLet(name, bindings, (variable, e) :: toeval, body, env) =>
      Action.push(FrameLet(variable, (name, v) :: bindings, toeval, body, env), e, env, store)
    case FrameLetStar(name, bindings, body, env) => {
      val a = addr.variable(name, abs.bottom, t)
      val env1 = env.extend(name, a)
      val store1 = store.extend(a, v)
      bindings match {
        case Nil => evalBody(body, env1, store1)
        case (variable, exp) :: rest => Action.push(FrameLetStar(variable, rest, body, env1), exp, env1, store1)
      }
    }
    case FrameLetrec(a, Nil, body, env) => evalBody(body, env, store.update(a, v))
    case FrameLetrec(a, (a1, exp) :: rest, body, env) =>
      Action.push(FrameLetrec(a1, rest, body, env), exp, env, store.update(a, v))
    case FrameSet(name, env) => env.lookup(name) match {
      case Some(a) => Action.value(sabs.inject(false), store.update(a, v), Set(EffectWriteVariable(a)))
      case None => Action.error(UnboundVariable(name))
    }
    case FrameBegin(body, env) => evalBody(body, env, store)
    case FrameCond(cons, clauses, env) =>
      conditional(v, if (cons.isEmpty) { Action.value(v, store) } else { evalBody(cons, env, store) },
        clauses match {
          case Nil => Action.value(sabs.inject(false), store)
          case (exp, cons2) :: rest => Action.push(FrameCond(cons2, rest, env), exp, env, store)
        })
    case FrameCase(clauses, default, env) => {
      val fromClauses = clauses.flatMap({ case (values, body) =>
        if (values.exists(v2 => evalValue(v2.value) match {
          case None => false
          case Some(v2) => sabs.subsumes(v, v2)
        }))
          /* TODO: precision could be improved by restricting v to v2 */
          evalBody(body, env, store)
        else
          Action.none
      })
      /* TODO: precision could be improved in cases where we know that default is not
       * reachable */
      fromClauses.toSet ++ evalBody(default, env, store)
    }
    case FrameAnd(Nil, env) =>
      conditional(v, Action.value(v, store), Action.value(sabs.inject(false), store))
    case FrameAnd(e :: rest, env) =>
      conditional(v, Action.push(FrameAnd(rest, env), e, env, store), Action.value(sabs.inject(false), store))
    case FrameOr(Nil, env) =>
      conditional(v, Action.value(v, store), Action.value(sabs.inject(false), store))
    case FrameOr(e :: rest, env) =>
      conditional(v, Action.value(v, store), Action.push(FrameOr(rest, env), e, env, store))
    case FrameDefine(name, env) => throw new Exception(s"TODO: define not handled (no global environment)")
  }

  def parse(program: String): SchemeExp = Scheme.parse(program)
  override def initialBindings = primitives.bindings
}

/**
 * Extend base Scheme semantics with:
 *   - atomic evaluation: parts of some constructs can be evaluated atomically
 *     without needing to introduce more states in the state graph. For example,
 *     (+ 1 1) can directly be evaluated to 2 without modifying the store. Also,
 *     to evaluate (+ 1 (f)), we can directly push the continuation and jump to
 *     the evaluation of (f), instead of evaluating +, and 1 in separate states.
 */
class SchemeSemantics[Abs : IsSchemeLattice, Addr : Address, Time : Timestamp](primitives: Primitives[Addr, Abs])
    extends BaseSchemeSemantics[Abs, Addr, Time](primitives) {

  /** Tries to perform atomic evaluation of an expression. Returns the result of
    * the evaluation if it succeeded, otherwise returns None */
  protected def atomicEval(e: SchemeExp, env: Env, store: Sto): Option[(Abs, Set[Effect[Addr]])] = e match {
    case λ: SchemeLambda => Some((sabs.inject[SchemeExp, Addr]((λ, env)), Set()))
    case SchemeIdentifier(name, _) => env.lookup(name).flatMap(a => store.lookup(a).map(v => (v, Set(EffectReadVariable(a)))))
    case SchemeValue(v, _) => evalValue(v).map(value => (value, Set()))
    case _ => None
  }

   override protected def funcallArgs(f: Abs, fexp: SchemeExp, args: List[(SchemeExp, Abs)], toeval: List[SchemeExp], env: Env, store: Sto, t: Time): Actions = toeval match {
    case Nil => evalCall(f, fexp, args.reverse, store, t)
    case e :: rest => atomicEval(e, env, store) match {
      case Some((v, effs)) => funcallArgs(f, fexp, (e, v) :: args, rest, env, store, t).map(_.addEffects(effs))
      case None => Action.push(FrameFuncallOperands(f, fexp, e, args, rest, env), e, env, store)
    }
  }

  /**
   * Optimize the following pattern: when we see an ActionPush(frame, exp, env, store)
   * where exp is an atomic expression, we can atomically evaluate exp to get v,
   * and call stepKont(v, store, frame).
   */
  protected def optimizeAtomic(actions: Actions, t: Time): Actions = actions.flatMap({
    case ActionPush(frame, exp, env, store, effects) => atomicEval(exp, env, store) match {
      case Some((v, effs)) => stepKont(v, frame, store, t).map(_.addEffects(effs ++ effects))
      case None => Action.push(frame, exp, env, store, effects)
    }
    case action => action
  })

  override def stepEval(e: SchemeExp, env: Env, store: Sto, t: Time) =
    optimizeAtomic(super.stepEval(e, env, store, t), t)

  override def stepKont(v: Abs, frame: Frame, store: Sto, t: Time) =
    optimizeAtomic(super.stepKont(v, frame, store, t), t)
}