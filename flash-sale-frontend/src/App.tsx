import { Tabs, Layout, Typography } from 'antd';
import { ShoppingOutlined, SettingOutlined } from '@ant-design/icons';
import MallTab from './components/MallTab';
import AdminTab from './components/AdminTab';
import './App.css';

const { Header, Content } = Layout;
const { Title } = Typography;

function App() {
  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5' }}>
      <Header style={{
        background: '#fff',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        borderBottom: '1px solid #f0f0f0',
        boxShadow: '0 2px 8px rgba(0,0,0,0.06)'
      }}>
        <Title level={3} style={{ margin: 0, color: '#1890ff' }}>
          Flash Sale System
        </Title>
        <span style={{ marginLeft: 12, color: '#999', fontSize: 13 }}>
          Enterprise High-Concurrency Architecture
        </span>
      </Header>
      <Content style={{ padding: '24px', maxWidth: 1200, margin: '0 auto', width: '100%' }}>
        <Tabs
          defaultActiveKey="mall"
          size="large"
          items={[
            {
              key: 'mall',
              label: (
                <span>
                  <ShoppingOutlined />
                  Mall
                </span>
              ),
              children: <MallTab />,
            },
            {
              key: 'admin',
              label: (
                <span>
                  <SettingOutlined />
                  Admin & Debug
                </span>
              ),
              children: <AdminTab />,
            },
          ]}
        />
      </Content>
    </Layout>
  );
}

export default App;
