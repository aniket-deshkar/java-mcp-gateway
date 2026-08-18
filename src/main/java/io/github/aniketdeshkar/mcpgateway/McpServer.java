package io.github.aniketdeshkar.mcpgateway;

import java.util.List;
import java.util.Map;

public interface McpServer {
  String id();

  List<ToolDescriptor> discoverTools();

  ToolResult invoke(String toolName, Map<String, Object> arguments);

  ServerHealth health();
}
