/**
 * Copyright (C) 2016 Instacount Inc. (developers@instacount.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.instacount.appengine.counter.service;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import io.instacount.appengine.counter.Counter;
import io.instacount.appengine.counter.data.CounterData;
import io.instacount.appengine.counter.data.CounterData.CounterStatus;
import io.instacount.appengine.counter.data.CounterShardData;
import io.instacount.appengine.counter.exceptions.NoCounterExistsException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueue;
import com.google.appengine.api.taskqueue.dev.QueueStateInfo;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;

/**
 * Unit tests for deleting a counter via {@link ShardedCounterServiceImpl}.
 *
 * @author David Fuelling
 */
public class ShardedCounterServiceDeleteTest extends AbstractShardedCounterServiceTest
{
	@Before
	public void setUp() throws Exception
	{
		super.setUp();
	}

	@After
	public void tearDown()
	{
		super.tearDown();
	}

	// /////////////////////////
	// Unit Tests
	// /////////////////////////

	@Test(expected = RuntimeException.class)
	public void testDeleteCounter_Null()
	{
		shardedCounterService.delete(null);
	}

	@Test(expected = RuntimeException.class)
	public void testDeleteCounter_Empty()
	{
		shardedCounterService.delete("");
	}

	@Test(expected = RuntimeException.class)
	public void testDeleteCounter_Blank()
	{
		shardedCounterService.delete(" ");
	}

	@Test(expected = NoCounterExistsException.class)
	public void testDeleteCounter_NoneExists()
	{
		try
		{
			shardedCounterService.delete(TEST_COUNTER1);
		}
		catch (NoCounterExistsException e)
		{
			assertThat(e.getCounterName(), is(TEST_COUNTER1));
			throw e;
		}
	}

	@Test
	public void testDeleteCounterWith_NonDefaultQueue() throws InterruptedException
	{
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withDeleteCounterShardQueueName(DELETE_COUNTER_SHARD_QUEUE_NAME).build();

		shardedCounterService = new ShardedCounterServiceImpl(memcache, config);

		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.delete(TEST_COUNTER1);
		assertPostDeleteCallSuccess(TEST_COUNTER1);
	}

	@Test
	public void testDeleteCounterWith_NonDefaultQueueAndNonDefaultPath() throws InterruptedException
	{
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withDeleteCounterShardQueueName(DELETE_COUNTER_SHARD_QUEUE_NAME)
			.withRelativeUrlPathForDeleteTaskQueue("/coolpath").build();
		shardedCounterService = new ShardedCounterServiceImpl(memcache, config);

		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.delete(TEST_COUNTER1);
		assertPostDeleteCallSuccess(TEST_COUNTER1);
	}

	@Test
	public void testDeleteWith1Shard() throws InterruptedException
	{
		shardedCounterService.increment(TEST_COUNTER1, 1);
		final Counter counter1 = shardedCounterService.getCounter(TEST_COUNTER1).get();
		assertCounter(counter1, TEST_COUNTER1, BigInteger.valueOf(1L));

		shardedCounterService.delete(TEST_COUNTER1);
		assertPostDeleteCallSuccess(TEST_COUNTER1);

		shardedCounterService.increment(TEST_COUNTER2, 1);
		final Counter counter2 = shardedCounterService.getCounter(TEST_COUNTER2).get();
		assertCounter(counter2, TEST_COUNTER2, BigInteger.valueOf(1L));

		shardedCounterService.delete(TEST_COUNTER2);
		assertPostDeleteCallSuccess(TEST_COUNTER2);
	}

	@Test
	public void testDeleteWith3Shards() throws InterruptedException
	{
		// Use 3 shards
		shardedCounterService = initialShardedCounterService(3);
		// Fill in multiple shards
		for (int i = 0; i < 21; i++)
		{
			// Ensures that, statistically, 3 shards will be created
			shardedCounterService.increment(TEST_COUNTER1, 1);
		}

		// Fill in multiple shards
		for (int i = 0; i < 22; i++)
		{
			// Ensures that, statistically, 3 shards will be created
			shardedCounterService.increment(TEST_COUNTER2, 1);
		}

		// ///////////////
		// Verify CounterData Counts
		// ///////////////

		// Clear Memcache
		if (this.isMemcacheAvailable())
		{
			this.memcache.clearAll();
		}

		Counter counter1 = shardedCounterService.getCounter(TEST_COUNTER1).get();
		assertCounter(counter1, TEST_COUNTER1, BigInteger.valueOf(21L));

		Counter counter2 = shardedCounterService.getCounter(TEST_COUNTER2).get();
		assertCounter(counter2, TEST_COUNTER2, BigInteger.valueOf(22L));

		// ///////////////
		// Assert that 6 CounterShards Exist (3 for each CounterData)
		// ///////////////
		this.assertAllCounterShardsExists(TEST_COUNTER1, 3);
		this.assertAllCounterShardsExists(TEST_COUNTER2, 3);

		// ///////////////
		// Delete CounterData 1
		// ///////////////
		shardedCounterService.delete(TEST_COUNTER1);
		assertPostDeleteCallSuccess(TEST_COUNTER1);

		// ///////////////
		// Assert that Counter2 still has shards around
		// ///////////////
		assertAllCounterShardsExists(TEST_COUNTER2, 3);
	}

	@Test
	public void testDeleteWith10Shards() throws InterruptedException
	{
		// Use 10 shards
		shardedCounterService = initialShardedCounterService(10);
		// Fill in multiple shards
		for (int i = 0; i < 50; i++)
		{
			// Ensures that, statistically, 10 shards will be created with ~5
			// each
			shardedCounterService.increment(TEST_COUNTER1, 1);
		}

		// ///////////////
		// Verify CounterData Counts
		// ///////////////

		Counter counter1 = shardedCounterService.getCounter(TEST_COUNTER1).get();
		assertCounter(counter1, TEST_COUNTER1, BigInteger.valueOf(50L));

		// ///////////////
		// Assert that 10 CounterShards Exist
		// ///////////////

		assertAllCounterShardsExists(TEST_COUNTER1, 10);

		// ///////////////
		// Delete CounterData 1
		// ///////////////

		// See here:
		// http://stackoverflow.com/questions/6632809/gae-unit-testing-taskqueue-with-testbed
		// The dev app server is single-threaded, so it can't run tests in the
		// background properly. Thus, we test that the task was added to the
		// queue properly. Then, we manually run the shard-deletion code and
		// assert that it's working properly.

		// This asserts that the task was added to the queue properly...
		shardedCounterService.delete(TEST_COUNTER1);
		assertPostDeleteCallSuccess(TEST_COUNTER1);
	}

	/**
	 * Asserts that the {@code numExpectedTasksInQueue} matches the actual number of tasks in the queue.
	 */
	private void assertNumTasksInQueue(int numExpectedTasksInQueue)
	{
		LocalTaskQueue ltq = LocalTaskQueueTestConfig.getLocalTaskQueue();
		QueueStateInfo qsi = ltq.getQueueStateInfo()
			.get(QueueFactory.getQueue(DELETE_COUNTER_SHARD_QUEUE_NAME).getQueueName());
		assertEquals(numExpectedTasksInQueue, qsi.getTaskInfo().size());
	}

	/**
	 * After calling {@link ShardedCounterService#delete(String)}, the following code asserts that a task was properly
	 * added to a task queue, and then manually deletes the counterShards (simulating what would happen in a real task
	 * queue).
	 * 
	 * @throws InterruptedException
	 */
	private void assertPostDeleteCallSuccess(String counterName) throws InterruptedException
	{
		Counter counter = shardedCounterService.getCounter(counterName).get();
		Assert.assertEquals(CounterStatus.DELETING, counter.getCounterStatus());

		// See here:
		// http://stackoverflow.com/questions/6632809/gae-unit-testing-taskqueue-with-testbed
		// The dev app server is single-threaded, so it can't run tests in the
		// background properly. Thus, we test that the task was added to the
		// queue properly. Then, we manually run the shard-deletion code and
		// assert that it's working properly.

		if (countdownLatch.getCount() == 1)
		{
			this.waitForCountdownLatchThenReset();
		}

		// By this point, the task should be processed in the queue and should
		// not exist...
		this.assertNumTasksInQueue(0);

		this.shardedCounterService.onTaskQueueCounterDeletion(counterName);
		this.assertAllCounterShardsExists(counterName, 0);

		// Don't call shardedCounterService.getCounter(counterName), or it will
		// initialize a new CounterData and the test will fail!
		Key<CounterData> counterDataKey = CounterData.key(counterName);
		CounterData counterData = ObjectifyService.ofy().load().key(counterDataKey).now();
		assertTrue(counterData == null);
	}

	/**
	 * Does a "consistent" lookup for all counterShards to ensure they exist in the datastore.
	 */
	private void assertAllCounterShardsExists(String counterName, int numCounterShardsToGet)
	{
		for (int i = 0; i < numCounterShardsToGet; i++)
		{
			final Key<CounterShardData> shardKey = Key.create(CounterShardData.class, counterName + "-" + i);
			final CounterShardData counterShardData = ObjectifyService.ofy().load().key(shardKey).now();
			assertNotNull(counterShardData);
		}

		if (numCounterShardsToGet == 0)
		{
			// Assert that no counterShards exists
			Key<CounterShardData> shardKey = Key.create(CounterShardData.class,
				counterName + "-" + numCounterShardsToGet);
			CounterShardData counterShardData = ObjectifyService.ofy().load().key(shardKey).now();
			assertTrue(counterShardData == null);
		}
		else
		{
			// Assert that no more shards exist for this counterShard starting
			// at {@code numCounterShardsToGet}
			Key<CounterShardData> shardKey = Key.create(CounterShardData.class,
				counterName + "-" + numCounterShardsToGet);
			CounterShardData counterShardData = ObjectifyService.ofy().load().key(shardKey).now();
			assertTrue(counterShardData == null);
		}
	}

	private void waitForCountdownLatchThenReset() throws InterruptedException
	{
		if (countdownLatch.getCount() != 0)
		{
			countdownLatch.awaitAndReset(5L, TimeUnit.SECONDS);
		}
	}

}
