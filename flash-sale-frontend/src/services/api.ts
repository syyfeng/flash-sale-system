import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// ─── Order APIs ──────────────────────────────────────────────

export interface CreateOrderResult {
  success: boolean;
  orderId?: number;
  productId?: number;
  status?: string;
  message: string;
}

export const placeOrder = async (productId: number): Promise<CreateOrderResult> => {
  const response = await api.post<CreateOrderResult>(`/order/create?productId=${productId}`);
  return response.data;
};

export interface OrderStats {
  total: number;
  pendingPayment: number;
  paid: number;
  canceled: number;
}

export const getOrderStats = async (): Promise<OrderStats> => {
  const response = await api.get<OrderStats>('/order/stats');
  return response.data;
};

export const resetOrders = async (): Promise<{ message: string }> => {
  const response = await api.post('/order/reset');
  return response.data;
};

// ─── Inventory APIs ──────────────────────────────────────────

export interface ProductInfo {
  id: number;
  name: string;
  price: number;
  dbStock: number;
  redisStock: number;
}

export const getProducts = async (): Promise<ProductInfo[]> => {
  const response = await api.get<ProductInfo[]>('/inventory/products');
  return response.data;
};

export interface StockInfo {
  productId: number;
  redisStock: number;
  dbStock: number;
}

export const getStock = async (productId: number): Promise<StockInfo> => {
  const response = await api.get<StockInfo>(`/inventory/stock/${productId}`);
  return response.data;
};

/** Create a new product (auto-generated ID) */
export const createProduct = async (product: {
  name: string;
  price: number;
  stock: number;
}): Promise<any> => {
  const response = await api.post('/inventory/products', product);
  return response.data;
};

/** Update an existing product by ID */
export const updateProduct = async (
  id: number,
  product: { name?: string; price?: number; stock?: number }
): Promise<any> => {
  const response = await api.put(`/inventory/products/${id}`, product);
  return response.data;
};

export const resetInventory = async (stock?: number): Promise<{ message: string }> => {
  const response = await api.post('/inventory/reset', { stock: stock || 100 });
  return response.data;
};

// ─── Payment Config APIs ─────────────────────────────────────

export interface PaymentConfig {
  result: string;
  delayMs: number;
}

export const getPaymentConfig = async (): Promise<PaymentConfig> => {
  const response = await api.get<PaymentConfig>('/order/payment/config');
  return response.data;
};

export const setPaymentConfig = async (config: {
  result: string;
  delayMs: number;
}): Promise<{ message: string }> => {
  const response = await api.post('/order/payment/config', config);
  return response.data;
};

// ─── Stress Test Helper ──────────────────────────────────────

export interface StressTestResult {
  total: number;
  success: number;
  failed: number;
  duration: number;
  results: CreateOrderResult[];
}

/**
 * Simulate N concurrent users using Promise.all.
 * Each "user" sends a POST /api/order/create request simultaneously.
 */
export const runStressTest = async (
  productId: number,
  concurrency: number
): Promise<StressTestResult> => {
  const start = Date.now();

  const promises = Array.from({ length: concurrency }, () =>
    placeOrder(productId).catch((err) => ({
      success: false,
      message: err.message || 'Network error',
    } as CreateOrderResult))
  );

  const results = await Promise.all(promises);
  const duration = Date.now() - start;

  const success = results.filter((r) => r.success).length;
  const failed = results.filter((r) => !r.success).length;

  return { total: concurrency, success, failed, duration, results };
};
