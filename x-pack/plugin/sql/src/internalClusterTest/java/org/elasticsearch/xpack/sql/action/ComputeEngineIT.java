/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.sql.action;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xpack.sql.action.compute.data.Page;
import org.elasticsearch.xpack.sql.action.compute.planner.PlanNode;
import org.elasticsearch.xpack.sql.action.compute.transport.ComputeAction;
import org.elasticsearch.xpack.sql.action.compute.transport.ComputeRequest;

import java.util.List;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

public class ComputeEngineIT extends AbstractSqlIntegTestCase {

    public void testComputeEngine() {
        assertAcked(
            client().admin()
                .indices()
                .prepareCreate("test")
                .setSettings(Settings.builder().put("index.number_of_shards", randomIntBetween(1, 5)))
                .get()
        );
        for (int i = 0; i < 10; i++) {
            client().prepareBulk()
                .add(new IndexRequest("test").id("1" + i).source("data", "bar", "count", 42))
                .add(new IndexRequest("test").id("2" + i).source("data", "baz", "count", 44))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .get();
        }
        ensureYellow("test");

        List<Page> pages = client().execute(
            ComputeAction.INSTANCE,
            new ComputeRequest(
                PlanNode.builder(new MatchAllDocsQuery(), randomFrom(PlanNode.LuceneSourceNode.Parallelism.values()), "test")
                    .numericDocValues("count")
                    .avgPartial("count")
                    .exchange(PlanNode.ExchangeNode.Type.GATHER, PlanNode.ExchangeNode.Partitioning.SINGLE_DISTRIBUTION)
                    .avgFinal("count")
                    .buildWithoutOutputNode()
            )
        ).actionGet().getPages();
        logger.info(pages);
        assertEquals(1, pages.size());
        assertEquals(1, pages.get(0).getBlockCount());
        assertEquals(43, pages.get(0).getBlock(0).getLong(0));

        pages = client().execute(
            ComputeAction.INSTANCE,
            new ComputeRequest(
                PlanNode.builder(new MatchAllDocsQuery(), randomFrom(PlanNode.LuceneSourceNode.Parallelism.values()), "test")
                    .numericDocValues("count")
                    .exchange(PlanNode.ExchangeNode.Type.GATHER, PlanNode.ExchangeNode.Partitioning.SINGLE_DISTRIBUTION)
                    .buildWithoutOutputNode()
            )
        ).actionGet().getPages();
        logger.info(pages);
        assertEquals(20, pages.stream().mapToInt(Page::getPositionCount).sum());
    }
}
