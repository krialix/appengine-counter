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
package io.instacount.appengine.counter.data;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Unindex;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * Represents a shard for a named counter in the datastore. An individual shard is written to infrequently to allow the
 * counter in aggregate to be incremented rapidly. All shard counts for a given named counter will be added together to
 * assemble the actual count for any named counter.
 * 
 * @author David Fuelling
 */
@Entity
// Not cached via @Cache because the this counter provides its own caching mechanism.
@Getter
@Setter
@Unindex
@ToString
@EqualsAndHashCode(of = "id")
public class CounterShardData
{
	static final String COUNTER_SHARD_KEY_SEPARATOR = "-";

	// The id of a CounterShardData is the name of the counter (for easy lookup
	// via a starts-with query) combined with the shardNumber. E.g.,
	// "CounterName-2" would be the counter with name "CounterName" and Shard
	// number 2.
	@Id
	private String id;

	// The total of this shard's count (not the total counter count)
	@Unindex
	private long count;

	@Unindex
	private DateTime creationDateTime;

	@Unindex
	private DateTime updatedDateTime;

	/**
	 * Default Constructor for Objectify
	 * 
	 * @deprecated Exists only for Objectify. Use the param-based constructors instead.
	 */
	@Deprecated
	public CounterShardData()
	{
		// Implemented for Objectify
	}

	/**
	 * Param-based Constructor
	 * 
	 * @param counterName
	 * @param shardNumber
	 */
	public CounterShardData(final String counterName, final int shardNumber)
	{
		// Preconditions checked by #constructCounterShardIdentifier
		this.setId(constructCounterShardIdentifier(counterName, shardNumber));

		this.creationDateTime = DateTime.now(DateTimeZone.UTC);
		this.updatedDateTime = DateTime.now(DateTimeZone.UTC);
	}

	/**
	 * Param-based Constructor
	 *
	 * @param counterShardDataKey
	 */
	public CounterShardData(final Key<CounterShardData> counterShardDataKey)
	{
		Preconditions.checkNotNull(counterShardDataKey);
		setId(counterShardDataKey.getName());
	}

	// /////////////////////////
	// Getters/Setters
	// /////////////////////////

	/**
	 * @return The last dateTime that an increment occurred.
	 */
	public DateTime getLastIncrement()
	{
		return getUpdatedDateTime();
	}

	/**
	 * Set the amount of this shard with a new {@code count}.
	 * 
	 * @param count
	 */
	public void setCount(long count)
	{
		this.count = count;
		this.setUpdatedDateTime(DateTime.now(DateTimeZone.UTC));
	}

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that key will be included in the returned Key
	 * heirarchy.
	 *
	 * @return
	 */
	public Key<CounterShardData> getTypedKey()
	{
		return Key.create(CounterShardData.class, this.getId());
	}

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that key will be included in the returned Key
	 * heirarchy.
	 */
	public com.google.appengine.api.datastore.Key getKey()
	{
		return this.getTypedKey().getRaw();
	}

	/**
	 * Helper method to set the internal identifier for this entity.
	 * 
	 * @param counterName
	 * @param shardNumber A unique identifier to distinguish shards for the same {@code counterName} from each other.
	 */
	@VisibleForTesting
	static String constructCounterShardIdentifier(final String counterName, final int shardNumber)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");
		Preconditions.checkArgument(shardNumber >= 0, "shardNumber must be greater than or equal to 0!");

		return counterName + COUNTER_SHARD_KEY_SEPARATOR + shardNumber;
	}

	/**
	 * Create a {@link Key Key<CounterShardData>}. Keys for this entity are not "parented" so that they can be added
	 * under high volume load in a given application. Note that CounterData will be in a namespace specific.
	 *
	 * @param counterDataKey
	 * @param shardNumber
	 * @return
	 */
	public static Key<CounterShardData> key(final Key<CounterData> counterDataKey, final int shardNumber)
	{
		// Preconditions checked by #constructCounterShardIdentifier
		return Key.create(CounterShardData.class,
			constructCounterShardIdentifier(counterDataKey.getName(), shardNumber));
	}

    /**
     * Helper method to compute the shard number index from an instance of {@link Key} of type {@link CounterShardData}.
     * This method assumes that the "name" field of a counter shard key will end in a dash, followed by the shard
     * index.
     *
     * @param counterShardDataKey
     * @return
     */
    public static Integer computeShardIndex(final Key<CounterShardData> counterShardDataKey) {
        Preconditions.checkNotNull(counterShardDataKey);

        final String key = counterShardDataKey.getName();

        int lastDashIndex = key.lastIndexOf("-");

        final String shardIndexAsString = key.substring((lastDashIndex + 1), key.length());
        return Integer.valueOf(shardIndexAsString);
    }

}
