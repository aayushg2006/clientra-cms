// Types mirroring the Spring Boot DTOs.

export type Role = "ADMIN" | "SALES_EXEC" | "SUPPORT_AGENT";
export type CustomerType = "INDIVIDUAL" | "BUSINESS";
export type Segment = "RETAIL" | "SMB" | "ENTERPRISE" | "OTHER";
export type CustomerStatus = "LEAD" | "ACTIVE" | "INACTIVE" | "CHURNED";
export type Source = "WEBSITE" | "REFERRAL" | "WALK_IN" | "CAMPAIGN" | "OTHER";
export type AddressType = "BILLING" | "SHIPPING";
export type InteractionType = "CALL" | "EMAIL" | "MEETING" | "NOTE" | "DEMO" | "QUOTE";
export type OrderStatus = "CONFIRMED" | "SHIPPED" | "DELIVERED" | "CANCELLED";
export type PaymentStatus = "PENDING" | "PAID" | "REFUNDED";
export type TicketChannel = "EMAIL" | "PHONE" | "CHAT" | "WEB" | "WALK_IN";
export type TicketCategory = "DELIVERY" | "PRODUCT_QUALITY" | "BILLING" | "ACCOUNT" | "OTHER";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH";
export type TicketStatus = "OPEN" | "IN_PROGRESS" | "AWAITING_CUSTOMER" | "RESOLVED";
export type NotificationType =
  | "FOLLOW_UP"
  | "SLA_BREACH"
  | "TICKET_ASSIGNED"
  | "TICKET_UPDATE"
  | "ORDER_PAID"
  | "CHURN_RISK"
  | "SYSTEM";

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface User {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  title?: string | null;
  phone?: string | null;
  initials: string;
  active: boolean;
  lastLoginAt?: string | null;
  createdAt?: string;
}

export interface UserRef {
  id: number;
  name: string;
  initials: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
  existingId?: number;
  existingCode?: string;
}

export interface TagDto {
  id: number;
  name: string;
  color: string;
}

export interface Address {
  id?: number;
  addressType: AddressType;
  line1: string;
  line2?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  country?: string | null;
  primary?: boolean;
}

export interface CustomerSummary {
  id: number;
  customerCode: string;
  firstName: string;
  lastName?: string | null;
  fullName: string;
  email: string;
  phone?: string | null;
  companyName?: string | null;
  customerType: CustomerType;
  segment: Segment;
  status: CustomerStatus;
  source?: Source | null;
  owner?: UserRef | null;
  lifetimeValue: number;
  churnScore?: number | null;
  tags: TagDto[];
  lastContactAt?: string | null;
  createdAt: string;
}

export interface TimelineEvent {
  kind: "INTERACTION" | "ORDER" | "TICKET" | "ACTIVITY";
  icon: string;
  title: string;
  actor: string;
  at: string;
  body?: string | null;
  badges: { label: string; tone: string }[];
  followUpAt?: string | null;
  slaDueAt?: string | null;
  link?: string | null;
}

export interface Customer360 extends CustomerSummary {
  jobTitle?: string | null;
  notes?: string | null;
  version: number;
  addresses: Address[];
  orderCount: number;
  openTickets: number;
  ticketCount: number;
  churnRisk: ChurnRisk;
  recentOrders: OrderSummary[];
  recentTickets: TicketSummary[];
  interactions: Interaction[];
  timeline: TimelineEvent[];
  lastContactAt?: string | null;
}

export interface ChurnRisk {
  score: number;
  level: "LOW" | "MEDIUM" | "HIGH";
  recencyDays?: number | null;
  frequency: number;
  monetary: number;
  openTickets: number;
  reasons: string[];
}

export interface Interaction {
  id: number;
  customerId: number;
  customerName: string;
  companyName?: string | null;
  type: InteractionType;
  subject: string;
  notes?: string | null;
  interactionAt: string;
  followUpAt?: string | null;
  followUpDone: boolean;
  user: UserRef;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  category?: string | null;
  unitPrice: number;
  gstRate: number;
  active: boolean;
}

export interface OrderItem {
  id?: number;
  productId: number;
  productName: string;
  sku?: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  customerCode?: string;
  orderDate: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  taxAmount: number;
  itemCount: number;
  createdBy?: UserRef | null;
}

export interface OrderDetail extends OrderSummary {
  items: OrderItem[];
  paidAt?: string | null;
}

export interface TicketSummary {
  id: number;
  ticketNumber: string;
  subject: string;
  customerId: number;
  customerName: string;
  channel: TicketChannel;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  slaDueAt: string;
  slaBreached: boolean;
  escalated: boolean;
  resolvedAt?: string | null;
  createdAt: string;
  assignee?: UserRef | null;
}

export interface TicketComment {
  id: number;
  body: string;
  internal: boolean;
  fromCustomer: boolean;
  author?: UserRef | null;
  authorName: string;
  createdAt: string;
}

export interface ActivityEntry {
  id: number;
  action: string;
  summary: string;
  user?: UserRef | null;
  createdAt: string;
}

export interface TicketDetail extends TicketSummary {
  description?: string | null;
  orderId?: number | null;
  orderNumber?: string | null;
  firstResponseAt?: string | null;
  customerEmail: string;
  comments: TicketComment[];
  activity: ActivityEntry[];
}

export interface NotificationItem {
  id: number;
  type: NotificationType;
  title: string;
  message?: string | null;
  link?: string | null;
  read: boolean;
  createdAt: string;
}

export interface CustomerStats {
  total: number;
  leads: number;
  active: number;
  inactive: number;
  churned: number;
}

export interface DuplicateCheck {
  duplicate: boolean;
  field?: "email" | "phone";
  existingId?: number;
  existingCode?: string;
  existingName?: string;
}

export interface CustomerLookup {
  id: number;
  name: string;
  customerCode: string;
  companyName?: string | null;
  email: string;
  status: CustomerStatus;
}

export interface ImportResult {
  totalRows: number;
  imported: number;
  skipped: number;
  errors: { row: number; email?: string | null; message: string }[];
}
