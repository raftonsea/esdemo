package com.example.es.esdemo.test.service;


import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class TestService {
    @Autowired
    private TransportClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String bulkSave() {
        int pageSize = 10000;
        int pageTotal = 50;
        for (int i = 0; i < pageTotal; i++) {
            int start = i * pageSize;
            List<Map<String, Object>> maps = jdbcTemplate.queryForList(" select * from pharmacy_drug_out_info limit  " + start + " ,  " + pageSize);
            doBulk(maps);
            System.err.println("处理完成：" + i);
        }
        return "success";
    }

    /**
     * bulk insert into  es
     *
     * @param maps
     */
    private void doBulk(List<Map<String, Object>> maps) {
        if (maps.isEmpty()) {
            return;
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        maps.forEach(map -> {
            handleData(map);
            IndexRequestBuilder indexBuilder = client.prepareIndex("gag_i_supervise_pharmacy_out", "pharmacy").setSource(map);
            bulkRequest.add(indexBuilder);
        });
        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            String failureMessage = bulkResponse.buildFailureMessage();
            System.err.println(failureMessage);
        }
    }

    void handleData(Map data) {
        String number = (String) data.get("number");
        double saleNumber = convert2Double(number);
        data.put("sale_product_number", saleNumber);
        String issuanceDate = (String) data.get("issuance_date");
        String issueDateCollated = convertStr2Date(issuanceDate);
        if (issueDateCollated == null) {
            System.err.println(" convert failed :  issuance_date  for value  " + issuanceDate);
        } else {
            data.put("issuance_date_collate", issueDateCollated);
        }
        String validityTime = (String) data.get("validity_time");
        String validityTimeCollated = convertStr2Date(validityTime);
        if (validityTimeCollated == null) {
            System.err.println(" convert failed :  validity_time  for value  " + validityTime);
        } else {
            data.put("validity_time_collate", validityTimeCollated);
        }
    }


    /**
     * @param dateStr
     * @return
     */
    String convertStr2Date(String dateStr) {
        if ("".equals(dateStr) || "-".equals(dateStr)) {
            return null;
        }
        try {
            String pattern = "yyyy-MM-dd";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDate localDate = LocalDate.parse(dateStr, formatter);
            return localDate.format(formatter);
        } catch (Exception e) {
        }
        try {
            String pattern1 = "yyyy-MM-dd HH:mm:ss";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern1);
            LocalDateTime localDateTime = LocalDateTime.parse(dateStr, formatter);
            return localDateTime.format(formatter);
        } catch (Exception e) {
        }
        return null;
    }


    /**
     * convert string to double
     *
     * @param data
     * @return
     */
    double convert2Double(String data) {
        return Double.valueOf(data).doubleValue();
    }


    public void queryDSL(Map param) {
        String productNameQuery = (String) param.get("productName");
        Integer start = Integer.valueOf((String) param.get("start"));
        Integer pageSize = Integer.valueOf((String) param.get("size"));
        BoolQueryBuilder builder = new BoolQueryBuilder();
        WildcardQueryBuilder productName = new WildcardQueryBuilder("product_name.raw", productNameQuery);
        builder.filter().add(productName);
        TermsQueryBuilder termsQueryBuilder = new TermsQueryBuilder("batch_number", new String[]{"无", "******", "-"});
        builder.mustNot().add(termsQueryBuilder);
        String query = builder.toString();
        System.err.println(query);
        SearchResponse sr = client.prepareSearch("gag_i_supervise_pharmacy_out")
                .setQuery(builder)
                .addSort("batch_number", SortOrder.DESC)
                .setFrom(start)
                .setSize(pageSize)
                .get();
        SearchHits hits = sr.getHits();
        long totalHits = hits.getTotalHits();
        System.err.println("totals ：" + totalHits);
        Iterator<SearchHit> iterator = hits.iterator();
        while (iterator.hasNext()) {
            SearchHit next = iterator.next();
            String nodeId = next.getShard().getNodeId();
            int id = next.getShard().getShardId().id();
            Map<String, Object> source = next.getSource();
            System.err.println("nodeId : " + nodeId + " shardId : " + id + " data : " + source);
        }

    }


    /**
     * sum aggregate nested in a term aggregate
     *
     * @param param
     */
    public void subSumAggregate(Map param) {
        String productNameQuery = (String) param.get("productName");
        String issuanceDate = (String) param.get("issuanceDate");
        BoolQueryBuilder builder = new BoolQueryBuilder();
        WildcardQueryBuilder wildcardQueryBuilder = new WildcardQueryBuilder("product_name.raw", productNameQuery);
        builder.filter().add(wildcardQueryBuilder);
        TermsQueryBuilder termsQueryBuilder = new TermsQueryBuilder("batch_number", new String[]{"无", "******", "-"});
        builder.mustNot().add(termsQueryBuilder);

        RangeQueryBuilder issuanceDateRange = new RangeQueryBuilder("issuance_date_collate");
        issuanceDateRange.gte(issuanceDate);
        builder.filter().add(issuanceDateRange);

        String queryString = builder.toString();
        System.err.println(queryString);

        TermsAggregationBuilder aggregationBuilder = AggregationBuilders
                .terms("ent_name_list")
                .field("ent_name")
                .order(Terms.Order.aggregation("ent_sum", false))
                .subAggregation(
                        AggregationBuilders.sum("ent_sum")
                                .field("sale_product_number")
                );

        SearchResponse sr = client.prepareSearch("gag_i_supervise_pharmacy_out")
                .setQuery(builder)
                .addAggregation(aggregationBuilder)
                .execute()
                .actionGet();

        Terms terms = sr.getAggregations().get("ent_name_list");
        List<? extends Terms.Bucket> buckets = terms.getBuckets();
        for (Terms.Bucket bucket : buckets) {
            String keyAsString = bucket.getKeyAsString();
            Sum sum = bucket.getAggregations().get("ent_sum");
            double entSum = sum.getValue();
            System.err.println(keyAsString + "  " + bucket + "  " + entSum);
        }
    }


    /**
     * cardinality aggregate
     *
     * @param param
     */
    public void cardinalityAggregate(Map param) {
        String productNameQuery = (String) param.get("productName");

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        WildcardQueryBuilder wildcardQueryBuilder = new WildcardQueryBuilder("product_name.raw", productNameQuery);
        boolQueryBuilder.filter().add(wildcardQueryBuilder);

        TermsQueryBuilder termsQueryBuilder = new TermsQueryBuilder("batch_number", new String[]{"无", "******", "-"});
        boolQueryBuilder.mustNot().add(termsQueryBuilder);

        CardinalityAggregationBuilder cardinalityAggregationBuilder = AggregationBuilders
                .cardinality("ent_name")
                .field("ent_name");

        SearchResponse sr = client.prepareSearch("gag_i_supervise_pharmacy_out")
                .setQuery(boolQueryBuilder)
                .addAggregation(cardinalityAggregationBuilder)
                .execute()
                .actionGet();

        Cardinality cardinality = sr.getAggregations().get("ent_name");
        long value = cardinality.getValue();
        System.err.println(value);

    }


    public void sumAgg(Map param) {
        BoolQueryBuilder builder = new BoolQueryBuilder();

        String productNameQuery = (String) param.get("productName");

        WildcardQueryBuilder wildcardQueryBuilder = new WildcardQueryBuilder("product_name.raw", productNameQuery);
        builder.filter().add(wildcardQueryBuilder);

        TermsQueryBuilder batchNum = new TermsQueryBuilder("batch_number", new String[]{"无", "******", "-"});
        builder.mustNot().add(batchNum);

        SumAggregationBuilder saleProNums = AggregationBuilders.sum("flow_nums").field("sale_product_number");

        SearchResponse sr = client.prepareSearch("gag_i_supervise_pharmacy_out")
                .setQuery(builder)
                .addAggregation(saleProNums).execute().actionGet();

        Sum saleSums = sr.getAggregations().get("flow_nums");
        double value = saleSums.getValue();
        System.err.println(value);
    }


    public static void main(String[] args) {
        TestService testService = new TestService();
        System.err.println(testService.convertStr2Date("2018-01-01"));
        System.err.println(testService.convertStr2Date("2018-01-01 12:20:30"));
        System.err.println(testService.convertStr2Date("2018-01-01-12:20:30"));
        System.err.println(testService.convertStr2Date(""));
        System.err.println(testService.convertStr2Date("-"));
        System.err.println(testService.convertStr2Date("+"));
    }

}
