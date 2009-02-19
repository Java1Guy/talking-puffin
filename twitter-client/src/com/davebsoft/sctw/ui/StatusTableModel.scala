package com.davebsoft.sctw.ui

import _root_.scala.xml.{NodeSeq, Node}
import filter.TagUser
import java.awt.event.{ActionEvent, ActionListener}
import java.util.{ArrayList, Collections}
import javax.swing.{SwingWorker, Timer}
import javax.swing.table.{DefaultTableModel, AbstractTableModel}
import twitter.StatusDataProvider

/**
 * Model providing status data to the JTable
 */
class StatusTableModel(statusDataProvider: StatusDataProvider) extends AbstractTableModel {
  /** How often, in ms, to fetch and load new data */
  private var updateFrequency = 120 * 1000;
  
  private var statuses = List[Node]()
  private val filteredStatuses = Collections.synchronizedList(new ArrayList[Node]())
  private val mutedIds = scala.collection.mutable.Set[String]()
  private var selectedTags = List[String]()
  private val colNames = List("Name", "Status")
  private var timer: Timer = null
  
  def getColumnCount = 2
  def getRowCount = filteredStatuses.size
  override def getColumnName(column: Int) = colNames(column)

  override def getValueAt(rowIndex: Int, columnIndex: Int) = {
    val status = filteredStatuses.get(rowIndex)
    val node = if (columnIndex == 0) status \ "user" \ "name" else status \ "text"
    node.text
  }
  
  def muteSelectedUsers(rows: Array[int]) {
    mutedIds ++= getUserIds(rows)
    filterAndNotify
  }
  
  def unMuteAll {
    mutedIds.clear
    filterAndNotify
  }
  
  def tagSelectedUsers(rows: Array[int], tag: String) {
    for (id <- getUserIds(rows)) {
      filter.tagUsers.add(new TagUser(tag, id))
    }
  }

  def setSelectedTags(selectedTags: List[String]) {
    this.selectedTags = selectedTags;
    filterAndNotify
  }
  
  private def getUserIds(rows: Array[int]): List[String] = {
    var ids = List[String]()
    for (i <- rows) {
      val status = filteredStatuses.get(i)
      ids ::= (status \ "user" \ "id").text
    }
    ids
  }
  
  private def createLoadTimer {
    timer = new Timer(updateFrequency, new ActionListener() {
      def actionPerformed(event: ActionEvent) {
        loadData
      }
    })
    timer.start
  }
  
  private def loadData {
    new SwingWorker[NodeSeq, Object] {
      def doInBackground = statusDataProvider.loadTwitterStatusData()
      override def done = {
        for (st <- get.reverse) {
          statuses = statuses ::: List(st)
        }
        filterAndNotify
      }
    }.execute
  }
  
  private def filterStatuses {
    filteredStatuses.clear
    for (st <- statuses) {
      var id = (st \ "user" \ "id").text
      if (! mutedIds.contains(id)) {
        if (tagFiltersInclude(id)) {
          filteredStatuses.add(st)
        }
      }
    }
  }
  
  private def tagFiltersInclude(id: String): Boolean = {
    if (selectedTags.length == 0) true else {
      for (tag <- selectedTags) {
        if (filter.tagUsers.contains(new TagUser(tag, id))) {
          return true
        }
      }
      false
    }
  }

  /**
   * Sets the update frequency, in seconds.
   */
  def setUpdateFrequency(updateFrequency: Int) {
    this.updateFrequency = updateFrequency * 1000
    if (timer != null && timer.isRunning) {
      timer.stop
    }

    if (updateFrequency > 0) {
      createLoadTimer
      loadData
    }
  }
  
  def clear {
    statuses = List[Node]()
    filterAndNotify
  }

  private def filterAndNotify {
    filterStatuses
    fireTableDataChanged
  }
}
  
