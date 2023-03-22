/*
*Copyright (c) 2021, Alibaba Group;
*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at

*   http://www.apache.org/licenses/LICENSE-2.0

*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright Havenask Contributors. See
 * GitHub history for details.
 */

package org.havenask.search.aggregations.bucket.sampler;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.search.DiversifiedTopDocsCollector;
import org.apache.lucene.search.DiversifiedTopDocsCollector.ScoreDocKey;
import org.apache.lucene.search.TopDocsCollector;
import org.havenask.HavenaskException;
import org.havenask.index.fielddata.AbstractNumericDocValues;
import org.havenask.search.aggregations.Aggregator;
import org.havenask.search.aggregations.AggregatorFactories;
import org.havenask.search.aggregations.bucket.DeferringBucketCollector;
import org.havenask.search.aggregations.support.ValuesSource;
import org.havenask.search.aggregations.support.ValuesSourceConfig;
import org.havenask.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public class DiversifiedNumericSamplerAggregator extends SamplerAggregator {

    private ValuesSource.Numeric valuesSource;
    private int maxDocsPerValue;

    DiversifiedNumericSamplerAggregator(
        String name,
        int shardSize,
        AggregatorFactories factories,
        SearchContext context,
        Aggregator parent,
        Map<String, Object> metadata,
        ValuesSourceConfig valuesSourceConfig,
        int maxDocsPerValue
    ) throws IOException {
        super(name, shardSize, factories, context, parent, metadata);
        assert valuesSourceConfig.hasValues();
        this.valuesSource = (ValuesSource.Numeric) valuesSourceConfig.getValuesSource();
        this.maxDocsPerValue = maxDocsPerValue;
    }

    @Override
    public DeferringBucketCollector getDeferringCollector() {
        bdd = new DiverseDocsDeferringCollector(this::addRequestCircuitBreakerBytes);
        return bdd;
    }

    /**
     * A {@link DeferringBucketCollector} that identifies top scoring documents
     * but de-duped by a key then passes only these on to nested collectors.
     * This implementation is only for use with a single bucket aggregation.
     */
    class DiverseDocsDeferringCollector extends BestDocsDeferringCollector {
        DiverseDocsDeferringCollector(Consumer<Long> circuitBreakerConsumer) {
            super(shardSize, context.bigArrays(), circuitBreakerConsumer);
        }

        @Override
        protected TopDocsCollector<ScoreDocKey> createTopDocsCollector(int size) {
            // Make sure we do not allow size > maxDoc, to prevent accidental OOM
            int minMaxDocsPerValue = Math.min(maxDocsPerValue, context.searcher().getIndexReader().maxDoc());
            return new ValuesDiversifiedTopDocsCollector(size, minMaxDocsPerValue);
        }

        @Override
        protected long getPriorityQueueSlotSize() {
            return SCOREDOCKEY_SIZE;
        }

        // This class extends the DiversifiedTopDocsCollector and provides
        // a lookup from havenask's ValuesSource
        class ValuesDiversifiedTopDocsCollector extends DiversifiedTopDocsCollector {

            private SortedNumericDocValues values;

            ValuesDiversifiedTopDocsCollector(int numHits, int maxHitsPerKey) {
                super(numHits, maxHitsPerKey);
            }

            @Override
            protected NumericDocValues getKeys(LeafReaderContext context) {
                try {
                    values = valuesSource.longValues(context);
                } catch (IOException e) {
                    throw new HavenaskException("Error reading values", e);
                }
                return new AbstractNumericDocValues() {

                    @Override
                    public boolean advanceExact(int target) throws IOException {
                        if (values.advanceExact(target)) {
                            if (values.docValueCount() > 1) {
                                throw new IllegalArgumentException(
                                        "Sample diversifying key must be a single valued-field");
                            }
                            return true;
                        } else {
                            return false;
                        }
                    }

                    @Override
                    public int docID() {
                        return values.docID();
                    }

                    @Override
                    public long longValue() throws IOException {
                        return values.nextValue();
                    }
                };
            }

        }

    }

}
