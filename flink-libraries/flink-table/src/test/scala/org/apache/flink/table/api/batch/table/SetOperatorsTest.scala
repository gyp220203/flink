/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.api.batch.table

import java.sql.Timestamp

import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.apache.flink.api.scala._
import org.apache.flink.table.api.Types
import org.apache.flink.table.api.scala._
import org.apache.flink.table.expressions.Null
import org.apache.flink.table.runtime.utils.CommonTestData.NonPojo
import org.apache.flink.table.utils.TableTestBase
import org.apache.flink.table.utils.TableTestUtil._
import org.junit.Test

class SetOperatorsTest extends TableTestBase {

  @Test
  def testInWithFilter(): Unit = {
    val util = batchTestUtil()
    val t = util.addTable[((Int, Int), String, (Int, Int))]("A", 'a, 'b, 'c)

    val elements = t.where('b === "two").select('a).as("a1")
    val in = t.select("*").where('c.in(elements))

    val expected = unaryNode(
      "DataSetCalc",
      binaryNode(
        "DataSetJoin",
        batchTableNode(0),
        unaryNode(
          "DataSetDistinct",
          unaryNode(
            "DataSetCalc",
            batchTableNode(0),
            term("select", "a AS a1"),
            term("where", "=(b, 'two')")
          ),
          term("distinct", "a1")
        ),
        term("where", "=(c, a1)"),
        term("join", "a", "b", "c", "a1"),
        term("joinType", "InnerJoin")
      ),
      term("select", "a", "b", "c")
    )

    util.verifyTable(in, expected)
  }

  @Test
  def testInWithProject(): Unit = {
    val util = batchTestUtil()
    val t = util.addTable[(Int, Timestamp, String)]("A", 'a, 'b, 'c)

    val in = t.select('b.in(Timestamp.valueOf("1972-02-22 07:12:00.333"))).as("b2")

    val expected = unaryNode(
      "DataSetCalc",
      batchTableNode(0),
      term("select", "IN(b, 1972-02-22 07:12:00.333) AS b2")
    )

    util.verifyTable(in, expected)
  }

  @Test
  def testUnionNullableTypes(): Unit = {
    val util = batchTestUtil()
    val t = util.addTable[((Int, String), (Int, String), Int)]("A", 'a, 'b, 'c)

    val in = t.select('a)
      .unionAll(
        t.select(('c > 0) ? ('b, Null(createTypeInformation[(Int, String)]))))

    val expected = binaryNode(
      "DataSetUnion",
      unaryNode(
        "DataSetCalc",
        batchTableNode(0),
        term("select", "a")
      ),
      unaryNode(
        "DataSetCalc",
        batchTableNode(0),
        term("select", "CASE(>(c, 0), b, null) AS _c0")
      ),
      term("union", "a")
    )

    util.verifyTable(in, expected)
  }

  @Test
  def testUnionAnyType(): Unit = {
    val util = batchTestUtil()
    val typeInfo = Types.ROW(
      new GenericTypeInfo(classOf[NonPojo]),
      new GenericTypeInfo(classOf[NonPojo]))
    val t = util.addJavaTable(typeInfo, "A", "a, b")

    val in = t.select('a).unionAll(t.select('b))

    val expected = binaryNode(
      "DataSetUnion",
      unaryNode(
        "DataSetCalc",
        batchTableNode(0),
        term("select", "a")
      ),
      unaryNode(
        "DataSetCalc",
        batchTableNode(0),
        term("select", "b")
      ),
      term("union", "a")
    )

    util.verifyJavaTable(in, expected)
  }
}
