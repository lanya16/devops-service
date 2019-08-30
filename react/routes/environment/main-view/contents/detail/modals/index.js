import React, { useMemo } from 'react';
import { observer } from 'mobx-react-lite';
import { Action } from '@choerodon/master';
import { Modal } from 'choerodon-ui/pro';
import HeaderButtons from '../../../../../../components/header-buttons';
import EnvDetail from '../../../../../../components/env-detail';
import Permission from '../../../../../resource/main-view/contents/environment/modals/permission';
import { useEnvironmentStore } from '../../../../stores';
import { useMainStore } from '../../../stores';
import { useDetailStore } from '../stores';
import ResourceSetting from './resource-setting/notificationsHome';
import useStore from './useStore';
import EnvCreateForm from '../../../modals/env-create';
import GroupForm from '../../../modals/GroupForm';
import DeployConfig from './deploy-config';

import './index.less';

const detailKey = Modal.key();
const envKey = Modal.key();
const groupKey = Modal.key();
const permissionKey = Modal.key();
const resourceKey = Modal.key();
const configKey = Modal.key();

const EnvModals = observer(() => {
  const modalStore = useStore();
  const modalStyle = useMemo(() => ({
    width: 380,
  }), []);
  const configModalStyle = useMemo(() => ({
    width: 'calc(100vw - 3.52rem)',
  }), []);
  const {
    treeDs,
    intlPrefix: currentIntlPrefix,
    prefixCls: currentPrefixCls,
    envStore: { getSelectedMenu },
    AppState: { currentMenuType: { id: projectId } },
  } = useEnvironmentStore();
  const { groupFormDs } = useMainStore();
  const {
    intl: { formatMessage },
    intlPrefix,
    prefixCls,
    detailStore: {
      getTabKey,
    },
    tabs: {
      SYNC_TAB,
      CONFIG_TAB,
      ASSIGN_TAB,
    },
    permissionsDs,
    gitopsLogDs,
    gitopsSyncDs,
    configDs,
  } = useDetailStore();

  function refresh() {
    treeDs.query();
    switch (getTabKey) {
      case SYNC_TAB: {
        gitopsSyncDs.query();
        gitopsLogDs.query();
        break;
      }
      case CONFIG_TAB:
        configDs.query();
        break;
      case ASSIGN_TAB:
        permissionsDs.query();
        break;
      default:
    }
  }

  function openEnvModal() {
    Modal.open({
      key: envKey,
      title: formatMessage({ id: `${currentIntlPrefix}.create` }),
      children: <EnvCreateForm intlPrefix={currentIntlPrefix} refresh={refresh} />,
      drawer: true,
      style: modalStyle,
    });
  }

  function openGroupModal() {
    Modal.open({
      key: groupKey,
      title: formatMessage({ id: `${currentIntlPrefix}.group.create` }),
      children: <GroupForm dataSet={groupFormDs} treeDs={treeDs} />,
      drawer: true,
      style: modalStyle,
      afterClose: () => {
        groupFormDs.current.reset();
      },
    });
  }

  function openEnvDetail() {
    Modal.open({
      key: detailKey,
      title: formatMessage({ id: `${intlPrefix}.modal.env-detail` }),
      children: <EnvDetail record={getSelectedMenu} isRecord={false} />,
      drawer: true,
      style: modalStyle,
      okCancel: false,
      okText: formatMessage({ id: 'close' }),
    });
  }

  async function addUsers(data) {
    const { id, objectVersionNumber } = getSelectedMenu;
    const users = {
      projectId,
      envId: id,
      objectVersionNumber,
      ...data,
    };
    return modalStore.addUsers(users);
  }

  function openPermission() {
    const { id, skipCheckPermission } = getSelectedMenu;
    modalStore.loadUsers(projectId, id);
    Modal.open({
      key: permissionKey,
      title: formatMessage({ id: `${intlPrefix}.modal.permission` }),
      drawer: true,
      style: modalStyle,
      children: <Permission
        store={modalStore}
        onOk={addUsers}
        intlPrefix={intlPrefix}
        prefixCls={prefixCls}
        skipPermission={skipCheckPermission}
        refresh={refresh}
      />,
      afterClose: () => {
        modalStore.setUsers([]);
      },
    });
  }

  function resourceSetting() {
    const { id } = getSelectedMenu;
    Modal.open({
      key: resourceKey,
      title: formatMessage({ id: `${currentIntlPrefix}.resource.setting` }),
      children: <ResourceSetting envId={id} />,
      drawer: true,
      style: {
        width: 1030,
      },
    });
  }


  function openConfigModal() {
    Modal.open({
      key: configKey,
      title: formatMessage({ id: `${currentIntlPrefix}.create.config` }),
      children: <DeployConfig
        intlPrefix={currentIntlPrefix}
        prefixCls={currentPrefixCls}
      />,
      drawer: true,
      style: configModalStyle,
    });
  }

  function getButtons() {
    const { active, synchro } = getSelectedMenu;
    const disabled = !active || !synchro;
    return [{
      name: formatMessage({ id: `${currentIntlPrefix}.create` }),
      icon: 'playlist_add',
      handler: openEnvModal,
      display: true,
      group: 1,
    }, {
      disabled,
      name: formatMessage({ id: `${currentIntlPrefix}.create.config` }),
      icon: 'playlist_add',
      handler: openConfigModal,
      display: true,
      group: 1,
    }, {
      disabled,
      name: formatMessage({ id: `${intlPrefix}.modal.permission` }),
      icon: 'authority',
      handler: openPermission,
      display: true,
      group: 1,
    }, {
      name: formatMessage({ id: `${intlPrefix}.modal.env-detail` }),
      icon: 'find_in_page',
      handler: openEnvDetail,
      display: true,
      group: 2,
    }, {
      name: formatMessage({ id: 'refresh' }),
      icon: 'refresh',
      handler: refresh,
      display: true,
      group: 2,
    }];
  }

  function getOtherBtn() {
    const actionData = [{
      text: formatMessage({ id: `${currentIntlPrefix}.group.create` }),
      action: openGroupModal,
    },
    {
      text: formatMessage({ id: `${currentIntlPrefix}.resource.setting` }),
      action: resourceSetting,
    }];
    return <Action data={actionData} />;
  }

  return <HeaderButtons items={getButtons()}>
    <div className={`${currentPrefixCls}-other-btn`}>{getOtherBtn()}</div>
  </HeaderButtons>;
});

export default EnvModals;