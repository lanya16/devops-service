package io.choerodon.devops.infra.dto;

import io.choerodon.mybatis.entity.BaseDTO;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;

/**
 *
 * @author wanghao
 * @Date 2020/4/2 17:00
 */
@Table(name = "devops_ci_pipeline")
public class DevopsCiPipelineDTO extends BaseDTO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ApiModelProperty("流水线名称")
    private String name;
    @ApiModelProperty("项目id")
    private Long projectId;
    @ApiModelProperty("流水线关联应用服务id")
    private Long appServiceId;
    @ApiModelProperty("流水线触发方式")
    private String triggerType;
    @ApiModelProperty("是否启用")
    @Column(name = "is_enabled")
    private Boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAppServiceId() {
        return appServiceId;
    }

    public void setAppServiceId(Long appServiceId) {
        this.appServiceId = appServiceId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
