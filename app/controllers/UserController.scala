package controllers


import cats.ApplicativeError
import cats.effect.Async
import com.mediarithmics._
import com.mediarithmics.tag.TLong
import javax.inject._
import play.api.mvc._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import tag.LongConverter
import cats.syntax.either._
import cats.syntax.functor._
import com.mediarithmics.iodb._
import javax.persistence.EntityManager

//import com.mediarithmics.common._
//object User{
//  case class Shape[F[_]](id : F[TLong[UserId]], name: String)
//  type CreateRequest = Shape[Empty]
//  type Resource = Shape[Id]
//
////  import io.circe.generic.semiauto._
////  implicit val createRequestDecoder = deriveDecoder[Shape[Empty]]
//}
//
//object Group {
//  case class Shape[F[_]](id : F[TLong[GroupId]], name: String)
//  type CreateRequest = Shape[Empty]
//  type Resource = Shape[Id]
//}


object User {
  case class CreateRequest(name: String)
  case class Resource(id : TLong[UserId], name: String)
}

object Group {
  case class CreateRequest(name: String)
  case class Resource(id : TLong[GroupId], name: String)
}

object ControllerUtils {

  implicit def tlongEncoder[T]: Encoder[TLong[T]] =  Encoder[Long].asInstanceOf[Encoder[TLong[T]]]
  implicit def tlongDecoder[T]: Decoder[TLong[T]] =  Decoder[Long].asInstanceOf[Decoder[TLong[T]]]

  implicit class ParseOp(val request : Request[AnyContent]) extends AnyVal {

    def bodyAs[T : Decoder]: Either[Error, T] =  for {
      content <- request.body.asText.toRight[Error](
        DecodingFailure("Cannot extract string from http request body", Nil))
      value <- decode[T](content)
    } yield value

  }


}

import ControllerUtils._
@Singleton
class UserController @Inject()(cc: ControllerComponents,
                               userService: UserService[TransactionableIO, EntityManager],
                               groupService: GroupService[TransactionableIO, EntityManager],
                               Async : Async[TransactionableIO],
                              )
  extends AbstractController(cc) {

  implicit val A : ApplicativeError[TransactionableIO, Throwable] = Async

  def createUser(): Action[AnyContent] = Action.async { r : Request[AnyContent] =>
    val doCreate = for {
      userRequestE <- Async.delay(r.bodyAs[User.CreateRequest])
      userRequest <- userRequestE.liftTo[TransactionableIO]
      created <- userService.createUser(userRequest.name)
      response = User.Resource(created.getId.tag, created.getName)
    } yield Ok( response.asJson.noSpaces )

    doCreate.transact.unsafeToFuture()
  }


  def createGroup(): Action[AnyContent] = Action.async {   r : Request[AnyContent] =>
    val doCreate = for {
      groupRequestE <- Async.delay(r.bodyAs[Group.CreateRequest])
      groupRequest <- groupRequestE.liftTo[TransactionableIO]
      created <- groupService.createGroup(groupRequest.name)
      response = Group.Resource(created.getId.tag, created.getName)
    } yield Ok( response.asJson.noSpaces )

    doCreate.transact.unsafeToFuture()
  }

  def addUserToGroup(userId: Long, groupId:Long): Action[AnyContent] = Action.async {  _ : Request[AnyContent] =>
    userService
      .addUserToGroup(userId.tag[UserId], groupId.tag[GroupId])
      .as(Ok(""))
      .transact.unsafeToFuture()
  }

  def getUserGroups(userId: Long): Action[AnyContent] = Action.async {  _ : Request[AnyContent] =>
    val doGet= for {
      groups <- userService.getUserGroups(userId.tag[UserId])
      resources = groups.map(g => Group.Resource(g.getId.tag, g.getName))
    } yield Ok(resources.asJson.noSpaces)

    doGet.transact.unsafeToFuture()
  }

}
