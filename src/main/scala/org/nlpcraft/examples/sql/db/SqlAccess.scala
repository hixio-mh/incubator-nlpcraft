/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nlpcraft.examples.sql.db

import java.sql.{Connection, SQLException}

import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.jakewharton.fliptables.FlipTable
import com.typesafe.scalalogging.LazyLogging
import org.h2.jdbcx.JdbcDataSource
import org.nlpcraft.model.tools.sqlgen.NCSqlQuery
import resource.managed

import scala.collection.JavaConverters._

/**
  * Database access service, single thread implementation.
  */
object SqlAccess extends LazyLogging {
    private final val LOG_ROWS = 10

    private var conn: Connection = _

    def select(qry: NCSqlQuery, logResult: Boolean): SqlResult = {
        if (conn == null)
            conn = {
                val ds = new JdbcDataSource

                ds.setUrl(SqlServer.H2_URL)

                ds.getConnection("", "")
            }

        try {
            managed { conn.prepareStatement(qry.getSql) } acquireAndGet { ps ⇒
                qry.getParameters.asScala.zipWithIndex.foreach { case (p, idx) ⇒ ps.setObject(idx + 1, p) }

                managed { ps.executeQuery() } acquireAndGet { rs ⇒
                    val md = rs.getMetaData
                    val cnt = md.getColumnCount

                    val cols = (1 to cnt).map(md.getColumnName)
                    var rows = List.empty[Seq[String]]

                    while (rs.next)
                        rows :+= (1 to cnt).map(i ⇒ {
                            val o = rs.getObject(i)

                            if (rs.wasNull()) "" else o.toString
                        })

                    if (logResult) {
                        logger.info(
                            s"Query executed successful" +
                                s" [\nsql=\n${SqlFormatter.format(qry.getSql)}" +
                                s", \nparameters=${qry.getParameters.asScala.mkString(",")}" +
                                s", \nrows=${rows.size}" +
                                s"]"
                        )

                        logger.info(s"Execution result, first $LOG_ROWS lines...")

                        var data = rows.take(LOG_ROWS).toArray.map(_.toArray)

                        if (rows.nonEmpty && rows.size > LOG_ROWS)
                            data = data ++  Array(cols.indices.map(_ ⇒ "...").toArray)

                        logger.info(s"\n${FlipTable.of(cols.toArray, data)}")
                    }

                    SqlResult(cols, rows)
                }
            }
        }
        catch {
            case e: SQLException ⇒
                conn = null

                logger.warn(
                    s"Query executed unsuccessful [sql=" +
                    s"\n${SqlFormatter.format(qry.getSql)}" +
                    s"\nparameters=${qry.getParameters.asScala.mkString(", ")}" +
                    s"\n]"
                )

                throw e
        }
    }
}