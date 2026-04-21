package com.trawhile.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicAboutInfo extends AboutInfo {

    public PublicAboutInfo(String version,
                           List<AboutInfoLicensesInner> licenses,
                           String sbomUrl,
                           String openApiUrl,
                           AboutInfoGdprSummary gdprSummary) {
        super(version, licenses, sbomUrl, openApiUrl, gdprSummary);
    }
}
