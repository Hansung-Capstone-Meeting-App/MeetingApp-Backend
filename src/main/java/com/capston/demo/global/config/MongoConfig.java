package com.capston.demo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.capston.demo.domain.meeting.repository",
        includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository.class
        )
)
public class MongoConfig {

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory dbFactory,
                                                       MongoMappingContext context) {
        DbRefResolver resolver = new DefaultDbRefResolver(dbFactory);
        MappingMongoConverter converter = new MappingMongoConverter(resolver, context);
        // _class 필드 무시: 클래스명 변경에 영향받지 않음
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }
}
