import { useState, useEffect, useRef, useCallback } from 'react';
import {
  Card, Button, Typography, Tag, Row, Col,
  Statistic, Modal, Spin, Badge, Space
} from 'antd';
import {
  ThunderboltFilled, ShoppingCartOutlined,
  CheckCircleOutlined, CloseCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { getProducts, getStock, placeOrder, type ProductInfo, type CreateOrderResult } from '../services/api';

const { Title, Text } = Typography;

export default function MallTab() {
  const [products, setProducts] = useState<ProductInfo[]>([]);
  const [loading, setLoading] = useState<Record<number, boolean>>({});
  const [stockMap, setStockMap] = useState<Record<number, number>>({});
  const [modalVisible, setModalVisible] = useState(false);
  const [modalResult, setModalResult] = useState<CreateOrderResult | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Fetch products on mount
  useEffect(() => {
    fetchProducts();
  }, []);

  // Poll stock every 2 seconds
  useEffect(() => {
    pollingRef.current = setInterval(() => {
      products.forEach((p) => {
        getStock(p.id)
          .then((info) => {
            setStockMap((prev) => ({ ...prev, [p.id]: info.redisStock }));
          })
          .catch(() => {});
      });
    }, 2000);

    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [products]);

  const fetchProducts = async () => {
    try {
      const data = await getProducts();
      setProducts(data);
      const map: Record<number, number> = {};
      data.forEach((p) => (map[p.id] = p.redisStock));
      setStockMap(map);
    } catch {
      // Products not loaded yet (server might be starting)
    }
  };

  const handleBuy = useCallback(async (productId: number) => {
    setLoading((prev) => ({ ...prev, [productId]: true }));
    try {
      const result = await placeOrder(productId);
      setModalResult(result);
      setModalVisible(true);
      // Refresh stock immediately
      const info = await getStock(productId);
      setStockMap((prev) => ({ ...prev, [productId]: info.redisStock }));
    } catch (error: any) {
      setModalResult({
        success: false,
        message: error.response?.data?.message || error.message || 'Network error',
      });
      setModalVisible(true);
    } finally {
      setLoading((prev) => ({ ...prev, [productId]: false }));
    }
  }, []);

  const getStockColor = (stock: number) => {
    if (stock <= 0) return '#ff4d4f';
    if (stock <= 10) return '#faad14';
    return '#52c41a';
  };

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Flash Sale Products</Title>
          <Text type="secondary">Real-time stock updates every 2 seconds</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchProducts}>
          Refresh
        </Button>
      </div>

      {products.length === 0 ? (
        <Card>
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
            <div style={{ marginTop: 16 }}>
              <Text type="secondary">Loading products... Make sure backend is running.</Text>
            </div>
          </div>
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {products.map((product) => {
            const stock = stockMap[product.id] ?? product.redisStock;
            const soldOut = stock <= 0;

            return (
              <Col xs={24} sm={12} md={8} key={product.id}>
                <Badge.Ribbon
                  text={soldOut ? 'SOLD OUT' : 'Flash Sale'}
                  color={soldOut ? '#999' : 'red'}
                >
                  <Card
                    className="product-card"
                    hoverable
                    cover={
                      <div style={{
                        height: 160,
                        background: soldOut
                          ? 'linear-gradient(135deg, #bbb 0%, #999 100%)'
                          : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'white',
                        fontSize: 48
                      }}>
                        <ShoppingCartOutlined />
                      </div>
                    }
                  >
                    <Title level={4} style={{ marginBottom: 4 }}>{product.name}</Title>

                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                        <Title level={3} style={{ margin: 0, color: '#f5222d' }}>
                          ${product.price}
                        </Title>
                        <div>
                          <Tag color={getStockColor(stock)}>
                            Stock: {stock}
                          </Tag>
                        </div>
                      </div>

                      <Statistic
                        title="Available Stock"
                        value={stock}
                        valueStyle={{ color: getStockColor(stock), fontSize: 20 }}
                        className={stock > 0 ? 'stock-updated' : ''}
                      />

                      <Button
                        type="primary"
                        size="large"
                        danger
                        block
                        loading={loading[product.id]}
                        disabled={soldOut}
                        onClick={() => handleBuy(product.id)}
                        icon={<ThunderboltFilled />}
                      >
                        {soldOut ? 'Sold Out' : 'Buy Now'}
                      </Button>
                    </Space>
                  </Card>
                </Badge.Ribbon>
              </Col>
            );
          })}
        </Row>
      )}

      {/* Result Modal */}
      <Modal
        title={
          modalResult?.success ? (
            <span style={{ color: '#52c41a' }}>
              <CheckCircleOutlined /> Order Submitted
            </span>
          ) : (
            <span style={{ color: '#ff4d4f' }}>
              <CloseCircleOutlined /> Order Failed
            </span>
          )
        }
        open={modalVisible}
        onOk={() => setModalVisible(false)}
        onCancel={() => setModalVisible(false)}
        footer={[
          <Button key="ok" type="primary" onClick={() => setModalVisible(false)}>
            OK
          </Button>,
        ]}
      >
        <div style={{ padding: '16px 0' }}>
          {modalResult?.success && modalResult.orderId && (
            <p>
              <strong>Order ID:</strong> {modalResult.orderId}
            </p>
          )}
          {modalResult?.status && (
            <p>
              <strong>Status:</strong>{' '}
              <Tag color="blue">{modalResult.status}</Tag>
            </p>
          )}
          <p>
            <strong>Message:</strong> {modalResult?.message}
          </p>
        </div>
      </Modal>
    </div>
  );
}
