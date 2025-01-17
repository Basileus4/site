package org.podval.archive19kislev.collector

import Xml.Ops
import scala.xml.Elem

final class Document(
  val collection: Collection,
  val name: String,
  val prev: Option[String],
  val next: Option[String],
  val translations: Seq[String]
) extends DocumentLike(collection.teiDirectory, name) {

  override def url: String = collection.documentUrl(name)

  private[this] val titleStmt: Elem = fileDesc.oneChild("titleStmt")

  val (partTitle: Option[Elem], title: Option[Elem], subTitle: Option[Elem]) = {
    val titles: Seq[Elem] = titleStmt.elemsFilter("title")
    def getTitle(name: String): Option[Elem] =
      titles.find(_.getAttribute("type") == name)

    (getTitle("part"), getTitle("main"), getTitle("sub"))
  }

  def author: Option[Elem] =
    titleStmt.optionalChild("author")

  def transcriber: Option[Elem] =
    titleStmt.elemsFilter("editor").find(_.attributeOption("role").contains("transcriber"))

  def publicationDate: Option[Elem] =
    fileDesc.oneChild("publicationStmt").optionalChild("date")

  def date: Option[String] =
    profileDesc.optionalChild("creation").map(_.oneChild("date").getAttribute("when"))

  def description: Option[Elem] =
    profileDesc.optionalChild("abstract").orElse(title)

  def language: Option[String] =
    profileDesc.oneChild("langUsage").elems("language").map(_.getAttribute("ident")).headOption

  val pages: Seq[Page] = for {
    pb <- body.descendants("pb")
    name = pb.getAttribute("n")
    isPresent = pb.attributeOption("facs").isDefined
  } yield Page(name, isPresent, this)

  override def persNames: Seq[Name] = names(teiDescendants("persName"))
  override def placeNames: Seq[Name] = names(teiDescendants("placeName"))
  override def orgNames: Seq[Name] = names(teiDescendants("orgName"))

  def addressee: Option[Name] =
    persNames.find(_.role.contains("addressee"))

  def writeWrappers(): Unit = {
    def quote(what: String): String = s"'$what'"

    val navigation: Seq[(String, String)] =
      prev.map(prev => Seq("prev" -> quote(prev))).getOrElse(Seq.empty) ++
      Seq("self" -> quote(name)) ++
      next.map(next => Seq("next" -> quote(next))).getOrElse(Seq.empty)

    def writeTeiWrapper(name: String, lang: Option[String]): Unit = {
      val nameWithLang: String = lang.fold(name)(lang => name + "-" + lang)

      Util.write(collection.documentsDirectory, s"$nameWithLang.html", Seq(
        "layout" -> "tei",
        "tei" -> s"'../${Collection.teiDirectoryName}/$nameWithLang.xml'",
        "facs" -> s"'../${Collection.viewersDirectoryName}/$name.html'"
      ) ++ (if (lang.isDefined || translations.isEmpty) Seq.empty else Seq("translations" -> translations.mkString("[", ", ", "]")))
        ++ navigation
      )
    }

    // TEI wrapper(s)
    writeTeiWrapper(name, None)
    for (lang <- translations) writeTeiWrapper(name, Some(lang))

    // Facsimile viewer
    Util.write(collection.viewersDirectory, s"$name.html", Seq(
      "layout" -> "facsimile",
      "images" -> pages.filter(_.isPresent).map(_.name).mkString("[", ", ", "]")
    ) ++ navigation
    )
  }
}
