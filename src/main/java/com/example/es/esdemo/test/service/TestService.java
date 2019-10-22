package com.example.es.esdemo.test.service;


import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TestService {
    @Autowired
    private TransportClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String bulkSave() {
        int pageSize = 5000;
        int pageTotal = 50;
        for (int i = 0; i < pageTotal; i++) {
            int start = i * pageSize;
            List<Map<String, Object>> maps = jdbcTemplate.queryForList(" select * from pharmacy_drug_out_info limit  " + start + " ,  " + pageSize);
            doBulk(maps);
            System.err.println("处理完成：" + i);
        }
        return "success";
    }

    private void doBulk(List<Map<String, Object>> maps) {
        if (maps.isEmpty()) {
            return;
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        maps.forEach(map -> {
            String number = (String) map.get("number");
            double saleNumber = convert2Double(number);
            map.put("sale_product_number", saleNumber);
            IndexRequestBuilder indexBuilder = client.prepareIndex("gag_i_supervise_pharmacy_out", "pharmacy").setSource(map);
            bulkRequest.add(indexBuilder);
        });
        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            String failureMessage = bulkResponse.buildFailureMessage();
            System.err.println(failureMessage);
        }
    }


    double convert2Double(String data) {
        return Double.valueOf(data).doubleValue();
    }


}
