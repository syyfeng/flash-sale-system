import { useState, useEffect, useCallback } from 'react';
import {
  Card, Button, Typography, Table, InputNumber, Input, Form,
  Space, Divider, Select, Statistic, Row, Col, Tag, Progress,
  Alert, Collapse, message,
  Segmented
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, RocketOutlined,
  SettingOutlined, ReloadOutlined, ExperimentOutlined,
  EditOutlined
} from '@ant-design/icons';
import {
  getProducts, createProduct, updateProduct, resetInventory, resetOrders,
  getOrderStats, runStressTest, getPaymentConfig, setPaymentConfig,
  type ProductInfo, type OrderStats, type StressTestResult, type PaymentConfig
} from '../services/api';

const { Text } = Typography;

export default function AdminTab() {
  // ─── State ──────────────────────────────────────────────────
  const [products, setProducts] = useState<ProductInfo[]>([]);
  const [orderStats, setOrderStats] = useState<OrderStats | null>(null);
  const [paymentCfg, setPaymentCfg] = useState<PaymentConfig>({ result: 'SUCCESS', delayMs: 50 });

  // Add product form
  const [productMode, setProductMode] = useState<'create' | 'edit'>('create');
  const [createForm, setCreateForm] = useState({ name: '', price: 0, stock: 100 });
  const [editForm, setEditForm] = useState<{ id: number; name: string; price: number; stock: number } | null>(null);

  // Stress test
  const [concurrency, setConcurrency] = useState(50);
  const [stressProductId, setStressProductId] = useState(1);
  const [stressResult, setStressResult] = useState<StressTestResult | null>(null);
  const [stressTesting, setStressTesting] = useState(false);

  // Reset
  const [resetStock, setResetStock] = useState(100);
  const [resetting, setResetting] = useState(false);

  // ─── Fetch data ─────────────────────────────────────────────
  const fetchAll = useCallback(async () => {
    try {
      const [prods, stats, pmtCfg] = await Promise.all([
        getProducts(),
        getOrderStats(),
        getPaymentConfig(),
      ]);
      setProducts(prods);
      setOrderStats(stats);
      setPaymentCfg(pmtCfg);
    } catch {
      // Backend might not be running
    }
  }, []);

  useEffect(() => {
    fetchAll();
    const interval = setInterval(fetchAll, 5000);
    return () => clearInterval(interval);
  }, [fetchAll]);

  const handleCreateProduct = async () => {
    if (!createForm.name.trim()) {
      message.warning('Product name is required');
      return;
    }
    try {
      const result = await createProduct(createForm);
      message.success(`Product created: ID #${result.id}`);
      setCreateForm({ name: '', price: 0, stock: 100 }); // Clear form
      fetchAll();
    } catch (e: any) {
      message.error('Failed to create product: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleUpdateProduct = async () => {
    if (!editForm) {
      message.warning('Select a product to edit first');
      return;
    }
    try {
      await updateProduct(editForm.id, {
        name: editForm.name,
        price: editForm.price,
        stock: editForm.stock,
      });
      message.success(`Product #${editForm.id} updated`);
      fetchAll();
    } catch (e: any) {
      message.error('Failed to update product: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleSelectProductForEdit = (record: ProductInfo) => {
    setEditForm({
      id: record.id,
      name: record.name,
      price: record.price,
      stock: record.dbStock,
    });
    setProductMode('edit');
  };

  const handlePaymentConfig = async () => {
    try {
      await setPaymentConfig(paymentCfg);
      message.success('Payment config updated');
    } catch (e: any) {
      message.error('Failed to update payment config');
    }
  };

  const handleStressTest = async () => {
    setStressTesting(true);
    setStressResult(null);
    try {
      const result = await runStressTest(stressProductId, concurrency);
      setStressResult(result);
      message.info(`Stress test complete: ${result.success}/${result.total} succeeded in ${result.duration}ms`);
      fetchAll();
    } catch (e: any) {
      message.error('Stress test failed: ' + (e.message || 'Unknown error'));
    } finally {
      setStressTesting(false);
    }
  };

  const handleReset = async () => {
    setResetting(true);
    try {
      const [orderRes, invRes] = await Promise.all([
        resetOrders(),
        resetInventory(resetStock),
      ]);
      message.success(`Reset complete! ${orderRes.message} | ${invRes.message}`);
      setStressResult(null);
      fetchAll();
    } catch (e: any) {
      message.error('Reset failed: ' + (e.message || 'Unknown error'));
    } finally {
      setResetting(false);
    }
  };

  // ─── Table columns ─────────────────────────────────────────
  const productColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    { title: 'Price', dataIndex: 'price', key: 'price', render: (v: number) => `$${v}` },
    {
      title: 'DB Stock',
      dataIndex: 'dbStock',
      key: 'dbStock',
      render: (v: number) => <Tag color={v > 0 ? 'green' : 'red'}>{v}</Tag>,
    },
    {
      title: 'Redis Stock',
      dataIndex: 'redisStock',
      key: 'redisStock',
      render: (v: number) => <Tag color={v > 0 ? 'blue' : 'red'}>{v}</Tag>,
    },
    {
      title: 'Diff',
      key: 'diff',
      render: (_: any, record: ProductInfo) => {
        const diff = record.dbStock - record.redisStock;
        return diff === 0
          ? <Tag color="green">In Sync</Tag>
          : <Tag color="orange">DB-Redis = {diff}</Tag>;
      },
    },
    {
      title: 'Action',
      key: 'action',
      width: 80,
      render: (_: any, record: ProductInfo) => (
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          onClick={() => handleSelectProductForEdit(record)}
        >
          Edit
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* ─── Order Stats ──────────────────────────── */}
        <Card title="Order Statistics" extra={<Button icon={<ReloadOutlined />} onClick={fetchAll} size="small">Refresh</Button>}>
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title="Total Orders" value={orderStats?.total ?? 0} />
            </Col>
            <Col span={6}>
              <Statistic
                title="Pending Payment"
                value={orderStats?.pendingPayment ?? 0}
                valueStyle={{ color: '#faad14' }}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Paid"
                value={orderStats?.paid ?? 0}
                valueStyle={{ color: '#52c41a' }}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Canceled"
                value={orderStats?.canceled ?? 0}
                valueStyle={{ color: '#ff4d4f' }}
              />
            </Col>
          </Row>
        </Card>

        {/* ─── Seller Panel ─────────────────────────── */}
        <Collapse
          defaultActiveKey={['seller']}
          items={[
            {
              key: 'seller',
              label: <><SettingOutlined /> Seller Panel</>,
              children: (
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  {/* Product Table */}
                  <Table
                    columns={productColumns}
                    dataSource={products}
                    rowKey="id"
                    size="small"
                    pagination={false}
                    bordered
                  />

                  {/* Create / Edit Product — Segmented Switch */}
                  <Card
                    size="small"
                    title={
                      <Space>
                        <span>Product Management</span>
                        <Segmented
                          size="small"
                          value={productMode}
                          onChange={(v) => setProductMode(v as 'create' | 'edit')}
                          options={[
                            { value: 'create', label: 'Create New', icon: <PlusOutlined /> },
                            { value: 'edit', label: 'Edit Existing', icon: <EditOutlined /> },
                          ]}
                        />
                      </Space>
                    }
                  >
                    {productMode === 'create' ? (
                      /* ─── Create Mode ─── */
                      <div>
                        <Alert
                          type="info"
                          showIcon
                          style={{ marginBottom: 12 }}
                          message="Creates a new product with auto-generated ID. Initializes DB stock and Redis cache."
                        />
                        <Space wrap>
                          <Form.Item label="Name" style={{ marginBottom: 0 }}>
                            <Input
                              placeholder="Product name"
                              value={createForm.name}
                              onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                              style={{ width: 200 }}
                            />
                          </Form.Item>
                          <Form.Item label="Price" style={{ marginBottom: 0 }}>
                            <InputNumber
                              min={0}
                              value={createForm.price}
                              onChange={(v) => setCreateForm({ ...createForm, price: v || 0 })}
                            />
                          </Form.Item>
                          <Form.Item label="Initial Stock" style={{ marginBottom: 0 }}>
                            <InputNumber
                              min={0}
                              value={createForm.stock}
                              onChange={(v) => setCreateForm({ ...createForm, stock: v || 0 })}
                            />
                          </Form.Item>
                          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateProduct}>
                            Create Product
                          </Button>
                        </Space>
                      </div>
                    ) : (
                      /* ─── Edit Mode ─── */
                      <div>
                        <Alert
                          type="info"
                          showIcon
                          style={{ marginBottom: 12 }}
                          message='Click the "Edit" button on a product row above to load it here, then modify and save.'
                        />
                        {editForm ? (
                          <Space wrap>
                            <Form.Item label="ID" style={{ marginBottom: 0 }}>
                              <InputNumber value={editForm.id} disabled style={{ width: 80 }} />
                            </Form.Item>
                            <Form.Item label="Name" style={{ marginBottom: 0 }}>
                              <Input
                                value={editForm.name}
                                onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                                style={{ width: 200 }}
                              />
                            </Form.Item>
                            <Form.Item label="Price" style={{ marginBottom: 0 }}>
                              <InputNumber
                                min={0}
                                value={editForm.price}
                                onChange={(v) => setEditForm({ ...editForm, price: v || 0 })}
                              />
                            </Form.Item>
                            <Form.Item label="Stock" style={{ marginBottom: 0 }}>
                              <InputNumber
                                min={0}
                                value={editForm.stock}
                                onChange={(v) => setEditForm({ ...editForm, stock: v || 0 })}
                              />
                            </Form.Item>
                            <Button type="primary" icon={<EditOutlined />} onClick={handleUpdateProduct}>
                              Save Changes
                            </Button>
                          </Space>
                        ) : (
                          <Text type="secondary">No product selected. Click "Edit" on a table row above.</Text>
                        )}
                      </div>
                    )}
                  </Card>


                  {/* Payment Config */}
                  <Card size="small" title="Payment Service Config (Mock)">
                    <Space wrap>
                      <Form.Item label="Result" style={{ marginBottom: 0 }}>
                        <Select
                          value={paymentCfg.result}
                          onChange={(v) => setPaymentCfg({ ...paymentCfg, result: v })}
                          style={{ width: 150 }}
                          options={[
                            { value: 'SUCCESS', label: 'SUCCESS' },
                            { value: 'FAILURE', label: 'FAILURE' },
                            { value: 'TIMEOUT', label: 'TIMEOUT' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="Delay (ms)" style={{ marginBottom: 0 }}>
                        <InputNumber
                          min={0}
                          max={10000}
                          value={paymentCfg.delayMs}
                          onChange={(v) => setPaymentCfg({ ...paymentCfg, delayMs: v || 50 })}
                        />
                      </Form.Item>
                      <Button type="primary" onClick={handlePaymentConfig}>
                        Apply
                      </Button>
                    </Space>
                  </Card>
                </Space>
              ),
            },
          ]}
        />

        {/* ─── Debug / Stress Panel ─────────────────── */}
        <Collapse
          defaultActiveKey={['debug']}
          items={[
            {
              key: 'debug',
              label: <><ExperimentOutlined /> Debug / Stress Test</>,
              children: (
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  {/* Stress Tester */}
                  <Card size="small" title="Concurrency Stress Tester">
                    <Alert
                      type="info"
                      showIcon
                      style={{ marginBottom: 16 }}
                      message="Simulates N concurrent users hitting the Buy Now endpoint using Promise.all"
                    />
                    <Space wrap>
                      <Form.Item label="Product ID" style={{ marginBottom: 0 }}>
                        <InputNumber
                          min={1}
                          value={stressProductId}
                          onChange={(v) => setStressProductId(v || 1)}
                        />
                      </Form.Item>
                      <Form.Item label="Concurrent Users" style={{ marginBottom: 0 }}>
                        <InputNumber
                          min={1}
                          max={50000}
                          value={concurrency}
                          onChange={(v) => setConcurrency(v || 50)}
                        />
                      </Form.Item>
                      <Button
                        type="primary"
                        danger
                        icon={<RocketOutlined />}
                        loading={stressTesting}
                        onClick={handleStressTest}
                      >
                        {stressTesting ? 'Running...' : 'Launch Stress Test'}
                      </Button>
                    </Space>

                    {stressResult && (
                      <div style={{ marginTop: 16 }}>
                        <Divider orientation={"left" as any}>Results</Divider>
                        <Row gutter={16}>
                          <Col span={6}>
                            <Statistic title="Total Requests" value={stressResult.total} />
                          </Col>
                          <Col span={6}>
                            <Statistic
                              title="Succeeded"
                              value={stressResult.success}
                              valueStyle={{ color: '#52c41a' }}
                            />
                          </Col>
                          <Col span={6}>
                            <Statistic
                              title="Failed"
                              value={stressResult.failed}
                              valueStyle={{ color: '#ff4d4f' }}
                            />
                          </Col>
                          <Col span={6}>
                            <Statistic
                              title="Duration"
                              value={stressResult.duration}
                              suffix="ms"
                            />
                          </Col>
                        </Row>
                        <div style={{ marginTop: 12 }}>
                          <Text type="secondary">Success Rate:</Text>
                          <Progress
                            percent={Math.round((stressResult.success / stressResult.total) * 100)}
                            status={stressResult.success === stressResult.total ? 'success' : 'normal'}
                          />
                        </div>
                      </div>
                    )}
                  </Card>

                  {/* System Reset */}
                  <Card
                    size="small"
                    title="System Reset"
                    style={{ border: '1px solid #ff4d4f' }}
                  >
                    <Alert
                      type="warning"
                      showIcon
                      style={{ marginBottom: 16 }}
                      message="This will TRUNCATE orders, local_message, stock_log and reset stock"
                    />
                    <Space>
                      <Form.Item label="Reset Stock To" style={{ marginBottom: 0 }}>
                        <InputNumber
                          min={0}
                          value={resetStock}
                          onChange={(v) => setResetStock(v || 100)}
                        />
                      </Form.Item>
                      <Button
                        danger
                        icon={<DeleteOutlined />}
                        loading={resetting}
                        onClick={handleReset}
                      >
                        Reset Everything
                      </Button>
                    </Space>
                  </Card>
                </Space>
              ),
            },
          ]}
        />
      </Space>
    </div>
  );
}
