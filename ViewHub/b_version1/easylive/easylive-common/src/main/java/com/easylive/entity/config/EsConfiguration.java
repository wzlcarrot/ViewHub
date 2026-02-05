package com.easylive.entity.config;


import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;

@Configuration
@RequiredArgsConstructor
public class EsConfiguration extends AbstractElasticsearchConfiguration implements DisposableBean {

    private final AppConfig appConfig;

    private RestHighLevelClient restHighLevelClient;

    //销毁elasticsearchClient客户端实例。
    @Override
    public void destroy() throws Exception {
        if(restHighLevelClient!=null){
            restHighLevelClient.close();
        }
    }

    //创建elasticsearchClient客户端实例。
    @Override
    public RestHighLevelClient elasticsearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder().connectedTo(appConfig.getEsHostPort()).build();
        restHighLevelClient = RestClients.create(clientConfiguration).rest();


        return restHighLevelClient;
    }
}
