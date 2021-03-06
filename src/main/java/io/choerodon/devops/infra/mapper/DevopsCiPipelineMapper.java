package io.choerodon.devops.infra.mapper;

import java.util.List;

import io.choerodon.devops.api.vo.DevopsCiPipelineVO;
import io.choerodon.devops.infra.dto.DevopsCiPipelineDTO;
import io.choerodon.mybatis.common.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 〈功能简述〉
 * 〈Ci流水线Mapper〉
 *
 * @author wanghao
 * @Date 2020/4/2 18:01
 */
public interface DevopsCiPipelineMapper extends Mapper<DevopsCiPipelineDTO> {

    /**
     * 查询项目下流水线集合
     * @param projectId
     * @param name
     * @return
     */
    List<DevopsCiPipelineVO> queryByProjectIdAndName(@Param("projectId") Long projectId,
                                                     @Param("name") String name);

    /**
     * 根据id查询流水线（包含关联应用服务name,gitlab_project_id）
     * @param ciPipelineId
     * @return
     */
    DevopsCiPipelineVO queryById(@Param("ciPipelineId") Long ciPipelineId);

    /**
     * 停用流水线
     * @param ciPipelineId
     * @return
     */
    int disablePipeline(@Param("ciPipelineId") Long ciPipelineId);

    /**
     * 启用流水线
     * @param ciPipelineId
     * @return
     */
    int enablePipeline(@Param("ciPipelineId") Long ciPipelineId);
}
