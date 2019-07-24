package io.choerodon.devops.infra.dto;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.persistence.*;

import io.choerodon.mybatis.entity.BaseDTO;

/**
 * Created by ernst on 2018/5/12.
 */
@Table(name = "devops_app_share")
public class ApplicationShareDTO extends BaseDTO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long appId;
    private String contributor;
    private String description;
    private String category;
    private String imgUrl;
    private String publishLevel;
    private Boolean isActive;
    private Boolean isFree;
    private Boolean isSite;

    @Transient
    private String name;
    @Transient
    private String code;
    @Transient
    private Long organizationId;
    @Transient
    private List<ApplicationVersionDTO> applicationVersionDTOList;
    @Transient
    private Boolean isDeployed;
    @Transient
    private Date appUpdatedDate;
    @Transient
    private Date marketUpdatedDate;

    public Boolean getSite() {
        return isSite;
    }

    public void setSite(Boolean site) {
        isSite = site;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getContributor() {
        return contributor;
    }

    public void setContributor(String contributor) {
        this.contributor = contributor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getPublishLevel() {
        return publishLevel;
    }

    public void setPublishLevel(String publishLevel) {
        this.publishLevel = publishLevel;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public List<ApplicationVersionDTO> getApplicationVersionDTOList() {
        return applicationVersionDTOList;
    }

    public void setApplicationVersionDTOList(List<ApplicationVersionDTO> applicationVersionDTOList) {
        this.applicationVersionDTOList = applicationVersionDTOList;
    }

    public Boolean getDeployed() {
        return isDeployed;
    }

    public void setDeployed(Boolean deployed) {
        isDeployed = deployed;
    }

    public Date getAppUpdatedDate() {
        return appUpdatedDate;
    }

    public void setAppUpdatedDate(Date appUpdatedDate) {
        this.appUpdatedDate = appUpdatedDate;
    }

    public Date getMarketUpdatedDate() {
        return marketUpdatedDate;
    }

    public void setMarketUpdatedDate(Date marketUpdatedDate) {
        this.marketUpdatedDate = marketUpdatedDate;
    }

    public Boolean getFree() {
        return isFree;
    }

    public void setFree(Boolean free) {
        isFree = free;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApplicationShareDTO that = (ApplicationShareDTO) o;
        return Objects.equals(id, that.id)
                && Objects.equals(appId, that.appId)
                && Objects.equals(contributor, that.contributor)
                && Objects.equals(description, that.description)
                && Objects.equals(category, that.category)
                && Objects.equals(imgUrl, that.imgUrl)
                && Objects.equals(publishLevel, that.publishLevel)
                && Objects.equals(isActive, that.isActive);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, appId, contributor, description, category, imgUrl, publishLevel, isActive);
    }
}