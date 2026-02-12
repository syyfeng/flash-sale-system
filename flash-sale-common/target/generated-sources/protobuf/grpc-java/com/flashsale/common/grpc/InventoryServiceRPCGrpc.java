package com.flashsale.common.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.57.0)",
    comments = "Source: inventory.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class InventoryServiceRPCGrpc {

  private InventoryServiceRPCGrpc() {}

  public static final java.lang.String SERVICE_NAME = "inventory.InventoryServiceRPC";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.flashsale.common.grpc.DeductStockRequest,
      com.flashsale.common.grpc.DeductStockResponse> getDeductStockMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeductStock",
      requestType = com.flashsale.common.grpc.DeductStockRequest.class,
      responseType = com.flashsale.common.grpc.DeductStockResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.flashsale.common.grpc.DeductStockRequest,
      com.flashsale.common.grpc.DeductStockResponse> getDeductStockMethod() {
    io.grpc.MethodDescriptor<com.flashsale.common.grpc.DeductStockRequest, com.flashsale.common.grpc.DeductStockResponse> getDeductStockMethod;
    if ((getDeductStockMethod = InventoryServiceRPCGrpc.getDeductStockMethod) == null) {
      synchronized (InventoryServiceRPCGrpc.class) {
        if ((getDeductStockMethod = InventoryServiceRPCGrpc.getDeductStockMethod) == null) {
          InventoryServiceRPCGrpc.getDeductStockMethod = getDeductStockMethod =
              io.grpc.MethodDescriptor.<com.flashsale.common.grpc.DeductStockRequest, com.flashsale.common.grpc.DeductStockResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeductStock"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.flashsale.common.grpc.DeductStockRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.flashsale.common.grpc.DeductStockResponse.getDefaultInstance()))
              .setSchemaDescriptor(new InventoryServiceRPCMethodDescriptorSupplier("DeductStock"))
              .build();
        }
      }
    }
    return getDeductStockMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static InventoryServiceRPCStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InventoryServiceRPCStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InventoryServiceRPCStub>() {
        @java.lang.Override
        public InventoryServiceRPCStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InventoryServiceRPCStub(channel, callOptions);
        }
      };
    return InventoryServiceRPCStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static InventoryServiceRPCBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InventoryServiceRPCBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InventoryServiceRPCBlockingStub>() {
        @java.lang.Override
        public InventoryServiceRPCBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InventoryServiceRPCBlockingStub(channel, callOptions);
        }
      };
    return InventoryServiceRPCBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static InventoryServiceRPCFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<InventoryServiceRPCFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<InventoryServiceRPCFutureStub>() {
        @java.lang.Override
        public InventoryServiceRPCFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new InventoryServiceRPCFutureStub(channel, callOptions);
        }
      };
    return InventoryServiceRPCFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void deductStock(com.flashsale.common.grpc.DeductStockRequest request,
        io.grpc.stub.StreamObserver<com.flashsale.common.grpc.DeductStockResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeductStockMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service InventoryServiceRPC.
   */
  public static abstract class InventoryServiceRPCImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return InventoryServiceRPCGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service InventoryServiceRPC.
   */
  public static final class InventoryServiceRPCStub
      extends io.grpc.stub.AbstractAsyncStub<InventoryServiceRPCStub> {
    private InventoryServiceRPCStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InventoryServiceRPCStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InventoryServiceRPCStub(channel, callOptions);
    }

    /**
     */
    public void deductStock(com.flashsale.common.grpc.DeductStockRequest request,
        io.grpc.stub.StreamObserver<com.flashsale.common.grpc.DeductStockResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeductStockMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service InventoryServiceRPC.
   */
  public static final class InventoryServiceRPCBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<InventoryServiceRPCBlockingStub> {
    private InventoryServiceRPCBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InventoryServiceRPCBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InventoryServiceRPCBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.flashsale.common.grpc.DeductStockResponse deductStock(com.flashsale.common.grpc.DeductStockRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeductStockMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service InventoryServiceRPC.
   */
  public static final class InventoryServiceRPCFutureStub
      extends io.grpc.stub.AbstractFutureStub<InventoryServiceRPCFutureStub> {
    private InventoryServiceRPCFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected InventoryServiceRPCFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new InventoryServiceRPCFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.flashsale.common.grpc.DeductStockResponse> deductStock(
        com.flashsale.common.grpc.DeductStockRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeductStockMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_DEDUCT_STOCK = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_DEDUCT_STOCK:
          serviceImpl.deductStock((com.flashsale.common.grpc.DeductStockRequest) request,
              (io.grpc.stub.StreamObserver<com.flashsale.common.grpc.DeductStockResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getDeductStockMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.flashsale.common.grpc.DeductStockRequest,
              com.flashsale.common.grpc.DeductStockResponse>(
                service, METHODID_DEDUCT_STOCK)))
        .build();
  }

  private static abstract class InventoryServiceRPCBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    InventoryServiceRPCBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.flashsale.common.grpc.InventoryProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("InventoryServiceRPC");
    }
  }

  private static final class InventoryServiceRPCFileDescriptorSupplier
      extends InventoryServiceRPCBaseDescriptorSupplier {
    InventoryServiceRPCFileDescriptorSupplier() {}
  }

  private static final class InventoryServiceRPCMethodDescriptorSupplier
      extends InventoryServiceRPCBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    InventoryServiceRPCMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (InventoryServiceRPCGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new InventoryServiceRPCFileDescriptorSupplier())
              .addMethod(getDeductStockMethod())
              .build();
        }
      }
    }
    return result;
  }
}
