package com.github.hexx.charcount

import java.io.File
import java.util.Date

import scala.io.Source
import scala.util.Properties
import scala.util.control.Exception._

import scalax.file.{ Path, PathSet }

import org.scala_tools.time.Imports._

import com.mongodb.casbah.Imports._

import com.twitter.util.Eval

import org.jfree.chart.{ ChartFactory, ChartUtilities }
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.category.DefaultCategoryDataset

import com.github.hexx.common.NKF

trait Config {
  def pathMap: Map[String, PathSet[Path]]
}

object CharCount {
  val totalLabel = "total"

  val collection = MongoConnection()("charcount")("charcount")

  val charcountDir = Properties.userHome + "/.charcount/"

  val eval = new Eval
  val config = eval[Config](new File(charcountDir + "config.scala"))

  def countChar(copy: Boolean): Int = {
    val pathSet = if (copy) config.pathMap("copy") else config.pathMap("write")
    pathSet.map(path => NKF.fromFile(path.path, "-w").length).foldLeft(0)(_ + _)
  }

  def countMap: Map[String, Int] = {
    val totalPath = totalLabel -> ((config.pathMap values) reduce { (result, pathSet) => result +++ pathSet })
    config.pathMap + totalPath mapValues {
      pathSet => pathSet.map(path => NKF.fromFile(path.path, "-w").length).foldLeft(0)(_ + _)
    }
  }

  def createDBObject(date: DateTime): DBObject = {
    val obj = DBObject("date" -> date.toDate)
    val countMapSeq = (countMap mapValues (_.asInstanceOf[AnyRef])).toSeq
    obj("count") = DBObject(countMapSeq:_*)
    obj
  }

  def initDB {
    val today = (new DateTime).withTime(0, 0, 0, 0)
    collection += createDBObject(today)
    collection += createDBObject(today - 1.day)
  }

  def updateDB {
    val today = (new DateTime).withTime(0, 0, 0, 0)
    val countMapSeq = (countMap mapValues (_.asInstanceOf[AnyRef])).toSeq
    collection.findOne(DBObject("date" -> today.toDate)) match {
      case Some(obj) => {
        obj("count") = DBObject(countMapSeq:_*)
        collection += obj
      }
      case None => collection += createDBObject(today)
    }
  }

  def countOnDay(obj: DBObject, prev: DBObject): Map[String, Int] = {
    val countObj = new MongoDBObject { val underlying = obj.as[DBObject]("count") }
    val countPrev = new MongoDBObject { val underlying = prev.as[DBObject]("count") }
    (for {
      (label, count) <- countObj
      countPrev <- catching(classOf[NoSuchElementException]) opt countPrev.as[Int](label)
    } yield {
      label.asInstanceOf[String] -> (count.asInstanceOf[Int] - countPrev.asInstanceOf[Int])
    }).toMap
  }

  def printCount(obj: DBObject, prev: DBObject) {
    print(DateTimeFormat.forPattern("yyyy年MM月dd日").print(new DateTime(obj("date"))))
    print(": ")
    for ((label, count) <- countOnDay(obj, prev)) {
      print("%s: %6d ".format(label, count))
    }
    println
  }

  def printToday {
    val iter = collection.find.sort(DBObject("date" -> -1))
    val obj = iter.next
    val prev = iter.next
    printCount(obj, prev)
  }

  def printAll {
    collection.find.sort(DBObject("date" -> 1)) reduce {
      (prev, obj) => {
        printCount(obj, prev)
        obj
      }
    }
  }

  def drawChart {
    val dataset = new DefaultCategoryDataset
    val chart = ChartFactory.createStackedBarChart("CharCount", "日付", "文字", dataset, PlotOrientation.VERTICAL, true, true, true)
    collection.find.sort(DBObject("date" -> 1)) reduce {
      (prev, obj) => {
        for ((label, count) <- countOnDay(obj, prev); if label != totalLabel) {
          dataset.addValue(count, label, DateTimeFormat.forPattern("yyyy年MM月dd日").print(new DateTime(obj("date"))));  
        }
        obj
      }
    }
    ChartUtilities.saveChartAsPNG(new File(charcountDir + "charcount.png"), chart, 800, 500)
  }

  case class CommandLineOptions(all: Boolean = false,
                                noUpdate: Boolean = false,
                                resetDB: Boolean = false,
                                help: Boolean = false)

  def main(args: Array[String]) {
    import scopt._

    var options = CommandLineOptions()
    val parser = new OptionParser("charcount") {
      opt("a", "all", "print all", {
        options = options.copy(all = true)
      })
      opt("n", "no-update", "no update", {
        options = options.copy(noUpdate = true)
      })
      opt("r", "reset", "reset db", {
        options = options.copy(resetDB = true)
      })
      opt("h", "help", "help", {
        options = options.copy(help = true)
      })
    }
    if (!parser.parse(args)) {
      return
    }
    if (options.resetDB) {
      collection.db.dropDatabase
      return
    }
    if (options.help) {
      println(parser.usage)
      return
    }
    if (!options.noUpdate) {
      if (collection.count == 0) { 
        initDB
      } else {
        updateDB
      }
    }
    if (options.all) {
      printAll
    } else {
      printToday
    }
    drawChart
  }
}

class PomodoroTimerApp extends xsbti.AppMain {
  def run(config: xsbti.AppConfiguration) = {
    CharCount.main(config.arguments)
    Exit(0)
  }
}

case class Exit(val code: Int) extends xsbti.Exit
