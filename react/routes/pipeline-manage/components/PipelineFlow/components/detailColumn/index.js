import React, { useEffect, Fragment, useState } from 'react';

import { observer } from 'mobx-react-lite';
import { Tooltip } from 'choerodon-ui';
import { Button } from 'choerodon-ui/pro';
import { Modal } from 'choerodon-ui/pro';
import { Choerodon } from '@choerodon/boot';
import StatusDot from '../statusDot';
import CodeQuality from '../codeQuality';
import CodeLog from '../codeLog';
import './index.less';
import { handlePromptError } from '../../../../../../utils';

const jobType = {
  build: {
    name: '构建',
    children: [
      {
        name: '生成包名称：',
        type: '',
      },
      {
        name: '构建包路径：',
        type: '',
      },
      {
        name: '依赖库名称：',
        type: '',
      },
    ],
  },
  sonar: {
    name: '代码检查',
    children: [
      {
        name: '检测Bugs数量：',
        type: '',
      },
      {
        name: '检测代码异味数量：',
        type: '',
      },
      {
        name: '安全漏洞数量：',
        type: '',
      },
    ],
  },
};

function renderDuration(value) {
  let secondTime = parseInt(value, 10);// 秒
  let minuteTime = 0;// 分
  let hourTime = 0;// 小时
  if (secondTime > 60) {
    minuteTime = parseInt(secondTime / 60, 10);
    secondTime = parseInt(secondTime % 60, 10);
    if (minuteTime > 60) {
      hourTime = parseInt(minuteTime / 60, 10);
      minuteTime = parseInt(minuteTime % 60, 10);
    }
  }
  let result = `${parseInt(secondTime, 10)}秒`;

  if (minuteTime > 0) {
    result = `${parseInt(minuteTime, 10)}分${result}`;
  }
  if (hourTime > 0) {
    result = `${parseInt(hourTime, 10)}小时${result}`;
  }
  return result;
}

const DetailItem = (props) => {
  const [expand, setExpand] = useState(false);
  const {
    durationSeconds,
    itemStatus,
    startedDate,
    finishedDate,
    type,
    projectId,
    gitlabJobId,
    detailStore: {
      retryJob, getDetailData,
    },
    name,
    handleRefresh,
  } = props;

  const { gitlabProjectId, appServiceId } = getDetailData && getDetailData.devopsCiPipelineVO;

  function handleDropDown() {
    setExpand(!expand);
  }

  function renderMain() {
    return (
      <main style={{ display: expand ? 'block' : 'none' }}>
        {type && jobType[type].children.map(item => <div>
          <span>{item.name}</span>
          <span>-</span>
        </div>)}
      </main>
    );
  }

  function openDescModal() {
    Modal.open({
      title: '查看日志',
      key: Modal.key(),
      style: {
        width: 'calc(100vw - 3.52rem)',
      },
      children: <CodeLog gitlabProjectId={gitlabProjectId} projectId={projectId} gitlabJobId={gitlabJobId} />,
      drawer: true,
      okText: '关闭',
      footer: (okbtn) => (
        <Fragment>
          {okbtn}
        </Fragment>
      ),
    });
  }

  function openCodequalityModal() {
    Modal.open({
      title: '代码质量',
      key: Modal.key(),
      style: {
        width: 'calc(100vw - 3.52rem)',
      },
      children: <CodeQuality appServiceId={appServiceId} />,
      drawer: true,
      okText: '关闭',
      footer: (okbtn) => (
        <Fragment>
          {okbtn}
        </Fragment>
      ),
    });
  }

  async function handleJobRetry() {
    try {
      const res = await retryJob(projectId, gitlabProjectId, gitlabJobId);
      if (handlePromptError(res)) {
        handleRefresh();
        return true;
      }
      return false;
    } catch (error) {
      Choerodon.handleResponseError(error);
      return false;
    }
  }

  return (
    <div className="c7n-piplineManage-detail-column-item">
      <header>
        <StatusDot size={13} status={itemStatus} />
        <div className="c7n-piplineManage-detail-column-item-sub">
          <Tooltip title={name}>
            <span>{type && `【${jobType[type].name}】`}{name}</span>
          </Tooltip>
          {
            startedDate && finishedDate && <Tooltip title={`${startedDate}-${finishedDate}`}>
              <span>{startedDate}-{finishedDate}</span>
            </Tooltip>
          }
        </div>
        <Button
          className="c7n-piplineManage-detail-column-item-btn"
          icon={!expand ? 'arrow_drop_down' : 'arrow_drop_up'}
          shape="circle"
          funcType="flat"
          size="small"
          onClick={handleDropDown}
        />
      </header>

      {renderMain()}

      <footer>
        <Tooltip title="查看日志">
          <Button
            funcType="flat"
            shape="circle"
            size="small"
            icon="description-o"
            disabled={itemStatus === 'created'}
            onClick={openDescModal}
            color="primary"
          />
        </Tooltip>
        <Tooltip title="重试">
          <Button
            funcType="flat"
            disabled={!(itemStatus === 'success' || itemStatus === 'failed')}
            shape="circle"
            size="small"
            icon="refresh"
            color="primary"
            onClick={handleJobRetry}
          />
        </Tooltip>
        {
          type === 'sonar' && <Tooltip title="查看代码质量报告">
            <Button
              funcType="flat"
              shape="circle"
              size="small"
              onClick={openCodequalityModal}
              icon="policy-o"
              color="primary"
            />
          </Tooltip>
        }
        <span className="c7n-piplineManage-detail-column-item-time">
          <span>任务耗时：</span>
          <span>{durationSeconds ? `${renderDuration(durationSeconds)}` : '-'}</span>
        </span>
      </footer>
    </div>
  );
};

export default observer((props) => {
  // 抛出piplineName
  const { piplineName, piplineStatus, jobRecordVOList, seconds } = props;

  useEffect(() => {
  }, []);

  const renderItem = () => {
    const hasJobs = jobRecordVOList && jobRecordVOList.length > 0;
    const lists = hasJobs && jobRecordVOList.map((item, i) => {
      item.display = 'none';
      return item;
    });
    return hasJobs ? lists.map((item, index) => {
      const { status, gitlabJobId, stage } = item;
      return (
        <DetailItem
          key={gitlabJobId}
          piplineName={stage}
          itemStatus={status}
          {...props}
          {...item}
        />
      );
    }) : '无执行详情...';
  };

  return (
    <div className="c7n-piplineManage-detail-column">
      <div className="c7n-piplineManage-detail-column-header">
        <StatusDot size={17} status={piplineStatus} />
        <span>{piplineName}</span>
        {seconds && <span>{renderDuration(seconds)}</span>}
      </div>
      <div className="c7n-piplineManage-detail-column-lists">
        <h6>任务列表</h6>
        {renderItem()}
      </div>
      <div className="c7n-piplineManage-detail-column-type">
        <Tooltip title="自动流转">
          <span>A</span>
        </Tooltip>
        <span />
      </div>
    </div>
  );
});
