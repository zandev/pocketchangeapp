package com.pocketchangeapp.snippet

import java.util.Date

import scala.xml._
import net.liftweb._
import http._
import js._
import util._
import S._
import SHtml._
import scala.xml._
import Helpers._

import com.pocketchangeapp.model._
import com.pocketchangeapp.util.Util

class Accounts {

  def manage (xhtml : NodeSeq) : NodeSeq = {
    def deleteAccount (acct : Account) {
      // No cascade???
      acct.admins.foreach(_.delete_!)
      acct.viewers.foreach(_.delete_!)
      acct.entries.foreach({
	entry =>
	  entry.tags.foreach(_.delete_!) // Need to delete entry tags, too
	  entry.delete_!
      })
      acct.tags.foreach(_.delete_!)
      acct.notes.foreach(_.delete_!)
      acct.delete_!
    }

    User.currentUser.map({user =>
      user.accounts.flatMap({acct => 
	bind("acct", chooseTemplate("account", "entry", xhtml),
	     "name" -> Text(acct.name.is),
	     "description" -> Text(acct.description.is),
	     "actions" -> { link("/manage", () => acct.delete_!, Text("Delete")) ++ Text(" ") ++
			    link("/editAcct", () => currentAccountVar(acct), Text("Edit")) })
			   })
			}) openOr Text("You're not logged in")
  }

  object currentAccountVar extends RequestVar[Account]({
    Account.create.owner(User.currentUser.open_!)
  })
  def currentAccount = currentAccountVar.is

  def edit (xhtml : NodeSeq) : NodeSeq = {
    def doSave () = {
      
      currentAccount.validate match {
	case Nil => 
	  currentAccount.save
	redirectTo("/manage")
	case x => error(x)
      }
    }
    
    val acct = currentAccount

    bind("acct", xhtml,
	 "id" -> hidden(() => currentAccountVar(acct)),
	 "name" -> text(currentAccount.name.is, currentAccount.name(_)),
	 "description" -> text(currentAccount.description.is, currentAccount.description(_)),
	 "save" -> submit("Save", doSave))
  }

  final val graphChoices = List("history" -> "Balance History",
				"tagpie" -> "Pie chart by tag totals",
				"tagbar" -> "Bar chart of tag totals")

  def detail (xhtml: NodeSeq) : NodeSeq = S.param("name") match {
    case Full(acctName) => {
      Account.findByName(User.currentUser.open_!, acctName) match {
	case acct :: Nil => {
	  val tags = <a href={"/account/" + acct.name.is}>All tags</a> ++ Text(" ") ++ 
	         acct.tags.flatMap({tag => <a href={"/account/" + acct.name.is + "/" + tag.tag.is}>{tag.tag.is}</a> ++ Text(" ")})

	  // Some closure state for the Ajax calls
	  var startDate : Box[Date] = Empty
	  var endDate : Box[Date] = Empty
	  var graphType = "history"
	  val tag = S.param("tag")

	  // Ajax utility methods. Defined here to capture the closure vars defined above
	  def entryTable = buildExpenseTable(Expense.getByAcct(acct, startDate, endDate, Empty), tag, xhtml)

	  def updateGraph() = {
	    val dateClause : String = if (startDate.isDefined || endDate.isDefined) {
	      List(startDate.map("start=" + Util.noSlashDate.format(_)),
		   endDate.map("end=" + Util.noSlashDate.format(_))).filter(_.isDefined).map(_.open_!).mkString("?","&","")
	    } else ""

	    val url = "/graph/" + acctName + "/" + graphType + dateClause

	    JsCmds.SetHtml("entry_graph", <img src={url} />)
	  }

	  def updateTable() = {
	    JsCmds.SetHtml("entry_table", entryTable)
	  }

	  def updateStartDate (date : String) = {
	    startDate = Util.parseDate(date, Util.slashDate.parse)
	    updateGraph() & updateTable()
	  }

	  def updateEndDate (date : String) = {
	    endDate = Util.parseDate(date, Util.slashDate.parse)
	    updateGraph() & updateTable()
	  }
	   
	  def updateGraphType(newType : String) = {
	    graphType = newType
	    updateGraph()
	  }

	  bind("acct", xhtml, 
	       "name" -> acct.name.asHtml,
	       "balance" -> acct.balance.asHtml,
	       "tags" -> tags,
	       "startDate" -> SHtml.ajaxText("", updateStartDate),
	       "endDate" -> SHtml.ajaxText("", updateEndDate),
	       "graphType" -> SHtml.ajaxSelect(graphChoices, Full("history"), updateGraphType),
	       "graph" -> <img src={"/graph/" + acctName + "/history"} />,
	       "table" -> entryTable)
	}
	case _ => Text("Could not locate account " + acctName)
      }
    }
    case _ => Text("No account name provided")
  }

  def buildExpenseTable(entries : List[Expense], tag : Box[String], template : NodeSeq) = {
    val filtered = tag match {
      case Full(tag) => entries.filter(_.tags.exists(_.tag == tag)) // Can probably be made more efficient
      case _ => entries
    }

    filtered.flatMap({ entry =>
      bind("entry", chooseTemplate("acct", "tableEntry", template),
	   "date" -> Text(Util.slashDate.format(entry.dateOf.is)),
	   "desc" -> Text(entry.description.is),
	   "tags" -> Text(entry.tags.map(_.tag.is).mkString(", ")),
	   "amt" -> Text(entry.amount.toString),
	   "balance" -> Text(entry.currentBalance.toString))
		    })
  }
}