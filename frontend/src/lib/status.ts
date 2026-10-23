import type {
  CustomerStatus,
  InteractionType,
  OrderStatus,
  PaymentStatus,
  TicketChannel,
  TicketPriority,
  TicketStatus,
} from "./types";

export type Tone = "green" | "blue" | "amber" | "red" | "purple" | "teal" | "gray" | "orange";

export const CUSTOMER_STATUS: Record<CustomerStatus, { label: string; tone: Tone }> = {
  LEAD: { label: "Lead", tone: "blue" },
  ACTIVE: { label: "Active", tone: "green" },
  INACTIVE: { label: "Inactive", tone: "amber" },
  CHURNED: { label: "Churned", tone: "red" },
};

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; tone: Tone }> = {
  PENDING: { label: "Pending", tone: "amber" },
  PAID: { label: "Paid", tone: "green" },
  REFUNDED: { label: "Refunded", tone: "red" },
};

export const ORDER_STATUS: Record<OrderStatus, { label: string; tone: Tone }> = {
  CONFIRMED: { label: "Confirmed", tone: "amber" },
  SHIPPED: { label: "Shipped", tone: "blue" },
  DELIVERED: { label: "Delivered", tone: "green" },
  CANCELLED: { label: "Cancelled", tone: "red" },
};

export const TICKET_STATUS: Record<TicketStatus, { label: string; tone: Tone; dot: string }> = {
  OPEN: { label: "Open", tone: "orange", dot: "bg-orange-500" },
  IN_PROGRESS: { label: "In Progress", tone: "blue", dot: "bg-blue-600" },
  AWAITING_CUSTOMER: { label: "Awaiting Customer", tone: "purple", dot: "bg-purple-600" },
  RESOLVED: { label: "Resolved", tone: "green", dot: "bg-green-700" },
};

export const PRIORITY: Record<TicketPriority, { label: string; tone: Tone }> = {
  LOW: { label: "Low", tone: "teal" },
  MEDIUM: { label: "Medium", tone: "amber" },
  HIGH: { label: "High", tone: "red" },
};

export const CHANNEL_LABEL: Record<TicketChannel, string> = {
  EMAIL: "Email",
  PHONE: "Phone",
  CHAT: "Chat",
  WEB: "Web",
  WALK_IN: "Walk-in",
};

export const INTERACTION_ICON: Record<InteractionType, string> = {
  CALL: "📞",
  EMAIL: "✉️",
  MEETING: "🤝",
  NOTE: "📝",
  DEMO: "🤝",
  QUOTE: "✉️",
};

export const INTERACTION_LABEL: Record<InteractionType, string> = {
  CALL: "Call",
  EMAIL: "Email",
  MEETING: "Meeting",
  NOTE: "Note",
  DEMO: "Demo",
  QUOTE: "Quote",
};

/** Tag colours are stored on the tag; map the common hex values to pill tones. */
export function tagTone(color?: string | null): Tone {
  switch ((color ?? "").toUpperCase()) {
    case "#7C3AED":
      return "purple";
    case "#2563EB":
      return "blue";
    case "#D97706":
      return "amber";
    case "#DC2626":
      return "red";
    default:
      return "teal";
  }
}
