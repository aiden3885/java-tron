package org.tron.core.services.http;

import static org.tron.core.Constant.MAX_BLOBS_PER_BLOCK;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.BlobSidecarResponseMessage;
import org.tron.core.Wallet;

@Component
@Slf4j(topic = "API")
public class GetBlobSidecarsServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      long blockNum = Long.parseLong(request.getParameter("block"));
      List<String> rawIndices =
          Arrays.stream(request.getParameterValues("indices")).collect(Collectors.toList());
      List<Integer> indices = parseIndices(rawIndices);
      fillResponse(blockNum, indices, response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    doGet(request, response);
  }

  private List<Integer> parseIndices(List<String> rawIndices) {
    if (rawIndices == null || rawIndices.isEmpty()) {
      return new ArrayList<>();
    }

    Set<Integer> indices = new HashSet<>();
    List<String> invalidIndices = new ArrayList<>();
    for (String raw: rawIndices) {
      try {
        int index = Integer.parseInt(raw);
        if (index >= MAX_BLOBS_PER_BLOCK) {
          invalidIndices.add(raw);
          continue;
        }
        indices.add(index);
      } catch (NumberFormatException e) {
        invalidIndices.add(raw);
      }
    }
    if (!invalidIndices.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("requested blob indices %s are invalid", invalidIndices));
    }
    return new ArrayList<>(indices);
  }

  private void fillResponse(long blockNum, List<Integer> indices, HttpServletResponse response)
      throws IOException {
    BlobSidecarResponseMessage reply = wallet.getBlobSidecars(blockNum, indices);
    if (reply != null) {
      response.getWriter().println(Util.printBlobResponse(reply));
    } else {
      response.getWriter().println("{}");
    }
  }
}
