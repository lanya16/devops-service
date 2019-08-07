import React from 'react';
import { TabPage, Content, Header, Breadcrumb } from '@choerodon/boot';
import { Table } from 'choerodon-ui/pro';
import { Button } from 'choerodon-ui';
import { FormattedMessage } from 'react-intl';
import { useServiceDetailStore } from './stores';
import HeaderButtons from './HeaderButtons';
import TimePopover from '../../../components/timePopover/TimePopover';

const { Column } = Table;

const Version = (props) => {
  const {
    intl: { formatMessage },
    intlPrefix,
    prefixCls,
    versionDs,
  } = useServiceDetailStore();

  function renderTime({ value }) {
    return <TimePopover content={value} />;
  }

  return (
    <TabPage>
      <HeaderButtons />
      <Breadcrumb title="服务详情" />
      <Content>
        <Table dataSet={versionDs}>
          <Column name="version" />
          <Column name="creationDate" renderer={renderTime} />
        </Table>
      </Content>
    </TabPage>
  );
};

export default Version;