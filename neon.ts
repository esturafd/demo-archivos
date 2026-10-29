import { defineConfig } from "@neon/config/v1";

export default defineConfig({
  auth: true,
  dataApi: false,
  buckets: {
    files: {
      access: "private",
    },
  },
  branch: (branch) => {
    if (branch.isDefault) {
      return {};
    }
    if (!branch.exists) {
      // New non-default branches: auto-expire
      // Run `neon checkout <name>` to create a new branch with these settings
      return { ttl: "7d" };
    }
    return {};
  },
});
