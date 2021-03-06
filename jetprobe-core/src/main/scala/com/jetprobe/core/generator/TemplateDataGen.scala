package com.jetprobe.core.generator

import java.io.File
import java.util.UUID

import com.github.tototoshi.csv.CSVReader
import com.jetprobe.core.Predef.Session
import com.jetprobe.core.parser.{Expr, ExpressionParser}
import com.jetprobe.core.structure.ScenarioContext
import com.jetprobe.core.util.FileReader

import scala.util.Random
import scala.util.matching.Regex

/**
  * @author Shad.
  */
class TemplateDataGen(template: String, datasetPath: String, rows: Int) extends DataGenerator {

  lazy val gen = """\$\{([a-zA-Z])\w+\.?([a-zA-Z])\w+\}""".r
  val startPattern = "${"
  val endPattern = "}"

  var sequenceN = 0

  private[this] def getField(str: String): String = str.substring(2).takeWhile(ch => ch != '}')

  private[this] def getRegex(str: String): Regex = ("""\$\{""" + str + """\}""").r


  override def generate(session: Session): Option[Iterator[String]] = {
    ExpressionParser.parseAll(Seq(Expr(template), Expr(datasetPath)), session.attributes) match {
      case Right(mapped) =>
        val templateStr = FileReader.readFile(new File(mapped(template)))
        val regexMatches = gen findAllIn templateStr toList
        val reader = CSVReader.open(new File(mapped(datasetPath)))
        val dataset = reader.allWithHeaders().toArray
        val datasetSize = dataset.length

        def next: String = {
          var temp = templateStr
          val randomVal = dataset(Random.nextInt(datasetSize))

          regexMatches.toSet.foreach {
            str: String =>
              val strPattern = getField(str)
              val fieldVal = randomGen(strPattern,randomVal)
              /*val fieldVal = {
                if (strPattern.equals("Random.UUID"))
                  UUID.randomUUID().toString
                else
                  randomVal.getOrElse(strPattern, str)
              }*/
              temp = getRegex(strPattern).replaceAllIn(temp, fieldVal)
          }
          temp
        }

        Some(Iterator.fill[String](rows)(next))

      case Left(exception) => None
    }

  }

  private def randomGen(pattern : String,dataset : Map[String,String]) : String = pattern match {
    case "sequence.number" =>
      sequenceN = sequenceN + 1
      sequenceN.toString
    case "random.uuid" =>
      UUID.randomUUID().toString

    case _ => dataset(pattern).trim


  }


}
