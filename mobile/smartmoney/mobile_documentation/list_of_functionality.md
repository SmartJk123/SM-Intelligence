### 1. 🔐 Authentication, Security & User Onboarding

  • Interactive First-Time Onboarding: Visual walkthrough introducing the user to the app’s financial tools and features.
  • Sign-Up & Login: Secure authentication powered by Supabase GoTrue Auth with email and password.
  • Persistent Sessions & Splash Warm-Up: Auto-login on launch with an Android 12 splash screen and background data synchronization (WarmUpSyncScreen) so the app is instantly ready
  without flicker.
  • Multi-User Data Isolation: Switching users completely isolates Room database tables, preferences, and session tokens (UserProfileManager).
  • Profile Picture Customization: Update avatar directly using Android's system Photo Picker (PickVisualMedia) or camera, with local caching and removal options.
  • Secure Session Termination: One-tap sign out that securely purges local database tables and clears tokens.
  ──────
  ### 2. 🏠 Financial Overview & Dashboard (Home)

  • Consolidated Financial Snapshot: Real-time summary displaying total liquid funds, bank balances, investment valuation, and active budget allowances in a single view.
  • Live Money Movement Alerts: Dynamic visual card glow animations (TopCardGlowEvent) that trigger in real time whenever an inflow or outflow occurs.
  • Historical Month Navigation: Jump backward and forward between calendar months to analyze cash flow and spending history across specific time periods.
  • Quick-Action Shortcuts: One-tap navigation to link bank accounts, scan receipts, review budgets, and open wealth analytics.
  ──────
  ### 3. 🏦 Bank Account Management & Open Banking

  • Multi-Bank Connection: Link accounts from major supported Kenyan banks (KCB Bank, Equity Bank, NCBA, and Stanbic Bank).
  • Live Bank Environment & Webhook Health: Real-time status indicator showing whether bank connections are operating in Live Production or Sandbox, along with webhook health.
  • Multi-Account & Card Tracking: View balances across multiple linked cards (Debit and Credit) and account numbers.
  • Built-In Bank Transaction Simulator: Test simulated KCB or partner bank credit inflows and debit outflows directly from the app to observe real-time balance updates.
  ──────
  ### 4. 💳 Transaction Tracking & Cash Flow

  • Real-Time Transaction Feed: Live transaction history synchronized with the backend microservices and cached in Room SQLite for instant offline access.
  • Transaction Filtering: Filter transaction records instantly by All, Credit (Inflows), or Debit (Outflows).
  • 7-Day Trend Analysis: Background calculation of 7-day spending and income velocity displayed in cash flow curves.
  • Pending Transaction Staging: Newly scanned receipts or manual adjustments appear immediately as pending items before final settlement.
  ──────
  ### 5. 📷 On-Device Receipt & Invoice Scanner (OCR)

  • Camera & Gallery Receipt Capture: Snap receipts or invoices using the phone's camera or select images from the gallery.
  • 100% On-Device ML Text Recognition: Uses Google ML Kit to process receipts locally on the device—fast, private, and works completely offline.
  • Intelligent Data Extraction: Automated regex parsing to auto-detect:
      • Total invoice amount
      • Merchant / Vendor name
      • Transaction and due dates
  • One-Tap Conversion to Expense: Automatically converts scanned invoices into pending transactions.
  ──────
  ### 6. 📊 Budgeting & Expense Control

  • Category-Based Budgets: Create and manage budgets across spending categories (e.g., Groceries, Rent, Utilities, Entertainment).
  • Automated Expense Allocation: Matches debit transactions against corresponding budget categories automatically.
  • Visual Progress Bars: Real-time percentage bars showing allocated budget, current spend, and remaining allowance.
  • Overspending & Allocation Warnings: Visual alerts when categories approach or exceed their limits.
  ──────
  ### 7. 📈 Investment & Portfolio Tracking

  • Multi-Asset Holdings: Track diverse asset classes including Money Market Funds (MMF), Treasury Bills/Bonds, Stocks, Real Estate, and Crypto.
  • Principal vs. Valuation Tracking: Compare initial invested capital against live portfolio market valuation.
  • Maturity Countdown & Alerts: "Maturing Soon" badges highlight investments approaching expiration or rollover dates.
  • Full Portfolio CRUD: Add new assets, record revaluations, and delete liquidated investments.
  ──────
  ### 8. 🔮 Wealth Analytics & Forecasting Engine

  • Wealth Composition Breakdown: Visual breakdown of liquid cash vs. long-term illiquid assets.
  • Financial Health Diagnostics: Automated metrics calculating Runway (in months), Savings Rate, and Burn Rate.
  • Multi-Horizon Trajectory Simulation: Project net worth and wealth growth over 1 Year, 3 Years, 5 Years, or 10 Years.
  • "What-If" Scenario Modeling:
      • Budget Cut Presets: Simulate how cutting expenses by 10% or 20% accelerates future wealth.
      • Maturity Rollover Toggle: Toggle between reinvesting maturing assets or withdrawing them to liquid cash.
      • Scope Filters: Toggle between Pure Liquid Cash trajectory and Total Net Worth trajectory.

  ──────
  ### 9. 🤖 Raha: Personal AI Financial Assistant

  • Conversational Financial Chatbot: Built-in AI assistant accessible from anywhere in the app via an interactive slide-over sheet.
  • Natural Language Queries: Ask questions like:
      • "What is my total balance?"
      • "Check my budgets"
      • "Show recent transactions"
      • "Add an invoice"
  • Quick-Reply Suggestions: Contextual action chips for rapid one-tap queries.
  • Conversation Management: Clear or reset chat sessions at any time.
  ──────
  ### 10. 🔔 Smart Alerts & Notifications

  • High-Priority OS Push Alerts: Instant system notifications in the Android status bar for account inflows and outflows.
  • Notification Channels: Dedicated Android 8.0+ channel (KCB Banking Alerts) with custom vibration and heads-up popups.
  • Android 13+ Permission Compliance: Seamless runtime request for POST_NOTIFICATIONS.
  • In-App Notification Center: Unread badge counter, dismissible banners, and full notification history stored locally.
  ──────
  ### 11. 🎨 User Experience & Polish

  • Dynamic Dark & Light Mode: Custom Material 3 theme (EnergyTheme) that persists user preferences across restarts.
  • Physics-Based Bottom Navigation Bar: Floating, morphing navigation bar with spring physics that contracts and adjusts its opacity while the user scrolls.
  • Offline-First Functionality: Full offline access to accounts, transaction history, budgets, and investments even without internet connectivity.

