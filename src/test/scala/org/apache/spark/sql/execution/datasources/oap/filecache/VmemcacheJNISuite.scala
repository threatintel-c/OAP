/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.oap.filecache

import java.nio.{ByteBuffer, ByteOrder}

import org.junit.Assume
import sun.nio.ch.DirectBuffer

import org.apache.spark.sql.test.oap.SharedOapContext
import org.apache.spark.sql.types.LongType
import org.apache.spark.unsafe.{NativeLoader, Platform, VMEMCacheJNI}
import org.apache.spark.util.Utils


class VmemcacheJNISuite extends SharedOapContext{

  private var path: String = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    var exception = None: Option[Exception]
    try {
      NativeLoader.loadLibrary(VMEMCacheJNI.LIBRARY_NAME)
    } catch {
      case ex: RuntimeException =>
        exception = Some(ex)
    }
    Assume.assumeNoException(exception.getOrElse(null))
    path = Utils.createTempDir().getCanonicalPath()
  }

  test("test Native address ") {
    val initializeSize = 1024L * 1024 * 1024
    val success = VMEMCacheJNI.initialize(path, initializeSize)

    val key = "key"

    // copy length to offheap buffer
    val bbPut = ByteBuffer.allocateDirect(400)
    for(i <- 0 until 50) {
      bbPut.putLong(i.toLong)
    }
    bbPut.position(0)

    // put
    VMEMCacheJNI.putNative(key.getBytes, null, 0, key.length,
      bbPut.asInstanceOf[DirectBuffer].address(), 0, 400)

    // get
    val bbGet1 = ByteBuffer.allocateDirect(200)
    val bbGet2 = ByteBuffer.allocateDirect(200)
    // get 0- 200
    VMEMCacheJNI.getNative(key.getBytes, null, 0, key.length,
      bbGet1.asInstanceOf[DirectBuffer].address(), 0, 200)
    // get 200 - 400
    VMEMCacheJNI.getNative(key.getBytes, null, 0, key.length,
      bbGet2.asInstanceOf[DirectBuffer].address(), 200, 200)

    bbPut.asLongBuffer()
    bbGet1.asLongBuffer()
    bbGet2.asLongBuffer()
    for ( i <- 0 until 25) {
      assert(bbPut.getLong() == bbGet1.getLong())
    }

    for( i <- 0 until 25) {
      assert( bbPut.getLong() == bbGet2.getLong())
    }
  }

  test("platform test") {
    val src: Long = 1

    val dst = ByteBuffer.allocateDirect(100)
    val get = new Array[Byte](100)

    Platform.copyMemory(src, Platform.LONG_ARRAY_OFFSET,
      null, dst.asInstanceOf[DirectBuffer].address(), LongType.defaultSize)

    Platform.copyMemory(null, dst.asInstanceOf[DirectBuffer].address(),
      get, Platform.BYTE_ARRAY_OFFSET, 100)

    print(ByteBuffer.wrap(get).getLong())
  }

  test("exist test") {
    val initializeSize = 1024L * 1024 * 1024
    val success = VMEMCacheJNI.initialize(path, initializeSize)

    val key = "key"

    // copy length to offheap buffer
    val bbPut = ByteBuffer.allocateDirect(400)
    for(i <- 0 until 50) {
      bbPut.putLong(i.toLong)
    }
    bbPut.position(0)

    // put
    VMEMCacheJNI.putNative(key.getBytes, null, 0, key.length,
      bbPut.asInstanceOf[DirectBuffer].address(), 0, 400)

    val len = VMEMCacheJNI.exist(key.getBytes(), null, 0, key.getBytes().length)
    assert(len == 400)

    val bbGet = ByteBuffer.allocateDirect(len)
    VMEMCacheJNI.getNative(key.getBytes(), null, 0, key.getBytes().length,
      bbGet.asInstanceOf[DirectBuffer].address(), 0, len);

  }

  test("status test") {
    val initializeSize = 1024L * 1024 * 1024
    val success = VMEMCacheJNI.initialize(path, initializeSize)

    val key = "key"
    // copy length to offheap buffer
    val bbPut = ByteBuffer.allocateDirect(400)
    for(i <- 0 until 50) {
      bbPut.putLong(i.toLong)
    }
    bbPut.position(0)

    // put
    VMEMCacheJNI.putNative(key.getBytes, null, 0, key.length,
      bbPut.asInstanceOf[DirectBuffer].address(), 0, 400)

    val status = new Array[Long](3)
    VMEMCacheJNI.status(status)

    assert(status(0) == 0)
    assert(status(1) == 1)
    assert(status(2) >= 400)

  }
}
