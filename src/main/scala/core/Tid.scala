trait ThreadIdentifier[TID] {
  def name: String
  def initial: TID
  def thread[Exp : Expression, Time : Timestamp](exp: Exp, t: Time): TID
  def thread[Exp : Expression, Time : Timestamp](exp: Exp, t: Time, name: String): TID
  def thread[Exp : Expression, Time : Timestamp](t: Time, name: String): TID
}

object ThreadIdentifier {
  def apply[T : ThreadIdentifier]: ThreadIdentifier[T] = implicitly
}

trait ContextSensitiveTID

object ContextSensitiveTID {
  object Initial extends ContextSensitiveTID {
    override def toString = "main"
  }
  case class TID[Exp : Expression, Time : Timestamp](exp: Option[Exp], t: Time, name: Option[String]) extends ContextSensitiveTID {
    override def  toString = if (false && Timestamp[Time].name == "Concrete") {
      t.toString
    } else {
      name.getOrElse(exp match {
        case Some(e) => e.toString + "@" + Expression[Exp].pos(e).toString
        case None => t.toString
      })
    }
  }

  implicit object CSTIDThreadIdentifier extends ThreadIdentifier[ContextSensitiveTID] {
    def name = "ContextSensitive"
    def initial = Initial
    def thread[Exp : Expression, Time : Timestamp](exp: Exp, time: Time) = TID(Some(exp), time, None)
    def thread[Exp : Expression, Time : Timestamp](exp: Exp, time: Time, name: String) = TID(Some(exp), time, Some(name))
    def thread[Exp : Expression, Time : Timestamp](time: Time, name: String) = TID(None, time, Some(name))
  }
}

  /*
trait InsensitiveTID
object InsensitiveTID {
  object Initial extends InsensitiveTID {
    override def toString = "main"
  }
  object OnlyTid extends InsensitiveTID {
    override def toString = "t"
  }
  implicit object ITIDThreadIdentifier extends ThreadIdentifier[InsensitiveTID] {
    def name = "Insensitive"
    def initial = Initial
    def thread[Exp : Expression, Time : Timestamp](exp: Exp, time: Time) = OnlyTid
    def thread[Exp : Expression, Time : Timestamp](exp: Exp, time: Time, name: String) = OnlyTid
  }
}
   */
