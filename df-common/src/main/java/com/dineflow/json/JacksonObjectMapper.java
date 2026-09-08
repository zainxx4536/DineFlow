package com.dineflow.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * 自定义 Jackson 的 ObjectMapper，主要解决两个问题：
 * <p>
 * 1. 统一前后端 JSON 中日期时间的格式。
 * 该配置属于全局配置，所有经过该 ObjectMapper 处理的
 * LocalDate、LocalDateTime、LocalTime 字段都会遵循对应格式。
 * 如果某个字段需要单独指定格式，也可以使用 @JsonFormat 注解。
 * <p>
 * 2. JSON 中存在 Java 对象没有定义的字段时，反序列化不报错，
 * 而是忽略这些未知字段。
 * <p>
 * 需要将该 ObjectMapper 注册到 Spring MVC 的消息转换器中，
 * 才能用于接口请求和响应的 JSON 转换。
 */
public class JacksonObjectMapper extends ObjectMapper {

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    //public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    public JacksonObjectMapper() {
        super();
        //JSON 中存在 Java 对象未定义的属性时不报错
        this.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

        //配置日期时间类型的序列化和反序列化格式
        SimpleModule simpleModule = new SimpleModule()
                .addDeserializer(
                        LocalDateTime.class,
                        new LocalDateTimeDeserializer(
                                DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)
                        )
                )
                .addDeserializer(
                        LocalDate.class,
                        new LocalDateDeserializer(
                                DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)
                        )
                )
                .addDeserializer(
                        LocalTime.class,
                        new LocalTimeDeserializer(
                                DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)
                        )
                )
                .addSerializer(
                        LocalDateTime.class,
                        new LocalDateTimeSerializer(
                                DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)
                        )
                )
                .addSerializer(
                        LocalDate.class,
                        new LocalDateSerializer(
                                DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)
                        )
                )
                .addSerializer(LocalTime.class,
                        new LocalTimeSerializer(
                                DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)
                        )
                );

        //注册功能模块
        this.registerModule(simpleModule);
    }
}
