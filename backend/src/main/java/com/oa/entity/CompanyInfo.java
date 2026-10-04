package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 企业信息（单行，id=1）。 */
@Entity
@Table(name = "company_info")
@Getter
@Setter
public class CompanyInfo extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    @Column(name = "short_name", nullable = false, length = 64)
    private String shortName = "";

    @Column(name = "logo_url", nullable = false, length = 256)
    private String logoUrl = "";

    @Column(name = "address", nullable = false, length = 256)
    private String address = "";

    @Column(name = "phone", nullable = false, length = 32)
    private String phone = "";

    @Column(name = "email", nullable = false, length = 128)
    private String email = "";

    @Column(name = "credit_code", nullable = false, length = 32)
    private String creditCode = "";

    @Column(name = "description", nullable = false, length = 512)
    private String description = "";
}
