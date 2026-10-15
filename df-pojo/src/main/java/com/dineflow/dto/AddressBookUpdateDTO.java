package com.dineflow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
public class AddressBookUpdateDTO extends AddressBookAddDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 本次编辑依据的原地址版本。
     * 只用于 WHERE 比较，不作为新版本写入数据库。
     */
    private Integer expectedVersion;
}