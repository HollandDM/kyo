package kyo.chatgpt

import kyo._
import kyo.aspects._
import kyo.chatgpt.embeddings._

object contexts {

  case class Role(name: String) extends AnyVal

  object Role {
    val system: Role    = Role("system")
    val user: Role      = Role("user")
    val assistant: Role = Role("assistant")
    val function: Role  = Role("function")
  }

  case class Call(function: String, arguments: String)

  case class Message(
      role: Role,
      content: String,
      name: Option[String],
      call: Option[Call]
  )

  case class Context(
      messages: List[Message]
  ) {

    def add(
        role: Role,
        msg: String,
        name: Option[String],
        call: Option[Call]
    ): Context =
      Context(
          Message(role, msg, name, call) :: messages
      )

    def ++(that: Context): Context =
      Context(that.messages ++ messages)
  }

  object Contexts {
    val init = Context(Nil)

    def init(entries: (Role, String)*): Context = {
      def loop(ctx: Context, entries: List[(Role, String)]): Context =
        entries match {
          case Nil =>
            ctx
          case (role, msg) :: t =>
            loop(ctx.add(role, msg.stripMargin, None, None), t)
        }
      loop(init, entries.toList)
    }
  }
}
