package com.hmdp.dto;

import lombok.Data;

import java.util.List;


/**
 * 滚动分页查询结果的封装
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
