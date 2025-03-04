// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.remote;

import com.google.common.collect.ImmutableMap;
import org.openqa.selenium.Beta;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsDriver;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.interactions.Coordinates;
import org.openqa.selenium.interactions.Locatable;
import org.openqa.selenium.io.Zip;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.openqa.selenium.remote.DriverCommand.FIND_CHILD_ELEMENT;
import static org.openqa.selenium.remote.DriverCommand.FIND_CHILD_ELEMENTS;

public class RemoteWebElement implements WebElement, Locatable, TakesScreenshot, WrapsDriver  {

  private String foundBy;
  protected String id;
  protected RemoteWebDriver parent;
  protected FileDetector fileDetector;

  protected void setFoundBy(SearchContext foundFrom, String locator, String term) {
    this.foundBy = String.format("[%s] -> %s: %s", foundFrom, locator, term);
  }

  public void setParent(RemoteWebDriver parent) {
    this.parent = parent;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setFileDetector(FileDetector detector) {
    fileDetector = detector;
  }

  @Override
  public void click() {
    execute(DriverCommand.CLICK_ELEMENT(id));
  }

  @Override
  public void submit() {
    try {
      execute(DriverCommand.SUBMIT_ELEMENT(id));
    } catch (JavascriptException ex) {
      String message = "To submit an element, it must be nested inside a form element";
      throw new UnsupportedOperationException(message);
    }
  }

  @Override
  public void sendKeys(CharSequence... keysToSend) {
    if (keysToSend == null || keysToSend.length == 0) {
      throw new IllegalArgumentException("Keys to send should be a not null CharSequence");
    }
    for (CharSequence cs : keysToSend) {
      if (cs == null) {
        throw new IllegalArgumentException("Keys to send should be a not null CharSequence");
      }
    }

    String allKeysToSend = String.join("", keysToSend);

    List<File> files = Arrays.stream(allKeysToSend.split("\n"))
      .map(fileDetector::getLocalFile)
      .collect(Collectors.toList());
    if (!files.isEmpty() && !files.contains(null)) {
      allKeysToSend = files.stream()
        .map(this::upload)
        .collect(Collectors.joining("\n"));
    }

    execute(DriverCommand.SEND_KEYS_TO_ELEMENT(id, new CharSequence[]{allKeysToSend}));
  }

  private String upload(File localFile) {
    if (!localFile.isFile()) {
      throw new WebDriverException("You may only upload files: " + localFile);
    }

    try {
      String zip = Zip.zip(localFile);
      Response response = execute(DriverCommand.UPLOAD_FILE(zip));
      return (String) response.getValue();
    } catch (IOException e) {
      throw new WebDriverException("Cannot upload " + localFile, e);
    }
  }

  @Override
  public void clear() {
    execute(DriverCommand.CLEAR_ELEMENT(id));
  }

  @Override
  public String getTagName() {
    return (String) execute(DriverCommand.GET_ELEMENT_TAG_NAME(id))
      .getValue();
  }

  @Override
  public String getDomProperty(String name) {
    return stringValueOf(
      execute(DriverCommand.GET_ELEMENT_DOM_PROPERTY(id, name))
        .getValue());
  }

  @Override
  public String getDomAttribute(String name) {
    return stringValueOf(
      execute(DriverCommand.GET_ELEMENT_DOM_ATTRIBUTE(id, name))
        .getValue());
  }

  @Override
  public String getAttribute(String name) {
    return stringValueOf(
      execute(DriverCommand.GET_ELEMENT_ATTRIBUTE(id, name))
        .getValue());
  }

  @Override
  public String getAriaRole() {
    return stringValueOf(
      execute(DriverCommand.GET_ELEMENT_ARIA_ROLE(id))
        .getValue());
  }

  @Override
  public String getAccessibleName() {
    return stringValueOf(
      execute(DriverCommand.GET_ELEMENT_ACCESSIBLE_NAME(id))
        .getValue());
  }

  private static String stringValueOf(Object o) {
    if (o == null) {
      return null;
    }
    return String.valueOf(o);
  }

  @Override
  public boolean isSelected() {
    Object value = execute(DriverCommand.IS_ELEMENT_SELECTED(id))
      .getValue();
    try {
      return (Boolean) value;
    } catch (ClassCastException ex) {
      throw new WebDriverException("Returned value cannot be converted to Boolean: " + value, ex);
    }
  }

  @Override
  public boolean isEnabled() {
    Object value = execute(DriverCommand.IS_ELEMENT_ENABLED(id))
      .getValue();
    try {
      return (Boolean) value;
    } catch (ClassCastException ex) {
      throw new WebDriverException("Returned value cannot be converted to Boolean: " + value, ex);
    }
  }

  @Override
  public String getText() {
    Response response = execute(DriverCommand.GET_ELEMENT_TEXT(id));
    return (String) response.getValue();
  }

  @Override
  public String getCssValue(String propertyName) {
    Response response = execute(DriverCommand.GET_ELEMENT_VALUE_OF_CSS_PROPERTY(id, propertyName));
    return (String) response.getValue();
  }

  @Override
  public List<WebElement> findElements(By locator) {
    return parent.findElements(
      this,
      (using, value) -> FIND_CHILD_ELEMENTS(getId(), using, String.valueOf(value)),
      locator);
  }

  @Override
  public WebElement findElement(By locator) {
    return parent.findElement(
      this,
      (using, value) -> FIND_CHILD_ELEMENT(getId(), using, String.valueOf(value)),
      locator);
  }

  /**
   * @deprecated Rely on using {@link By.Remotable} instead
   */
  @Deprecated
  protected WebElement findElement(String using, String value) {
    throw new UnsupportedOperationException("`findElement` has been replaced by usages of " + By.Remotable.class);
  }

  /**
   * @deprecated Rely on using {@link By.Remotable} instead
   */
  @Deprecated
  protected List<WebElement> findElements(String using, String value) {
    throw new UnsupportedOperationException("`findElement` has been replaced by usages of " + By.Remotable.class);
  }

  @Override
  public SearchContext getShadowRoot() {
    Response response = execute(DriverCommand.GET_ELEMENT_SHADOW_ROOT(getId()));
    return (SearchContext) response.getValue();
  }

  protected Response execute(CommandPayload payload) {
    try {
      return parent.execute(payload);
    } catch (WebDriverException ex) {
      ex.addInfo("Element", this.toString());
      throw ex;
    }
  }

  protected Response execute(String command, Map<String, ?> parameters) {
    try {
      return parent.execute(command, parameters);
    } catch (WebDriverException ex) {
      ex.addInfo("Element", this.toString());
      throw ex;
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof WebElement)) {
      return false;
    }

    WebElement other = (WebElement) obj;
    while (other instanceof WrapsElement) {
      other = ((WrapsElement) other).getWrappedElement();
    }

    if (!(other instanceof RemoteWebElement)) {
      return false;
    }

    RemoteWebElement otherRemoteWebElement = (RemoteWebElement) other;

    return id.equals(otherRemoteWebElement.id);
  }

  /**
   * @return This element's hash code, which is a hash of its internal opaque ID.
   */
  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.openqa.selenium.internal.WrapsDriver#getWrappedDriver()
   */
  @Override
  public WebDriver getWrappedDriver() {
    return parent;
  }

  @Override
  public boolean isDisplayed() {
    Object value = execute(DriverCommand.IS_ELEMENT_DISPLAYED(id))
      .getValue();
    try {
      // See https://github.com/SeleniumHQ/selenium/issues/9266
      if (value == null) {
        return false;
      }
      return (Boolean) value;
    } catch (ClassCastException ex) {
      throw new WebDriverException("Returned value cannot be converted to Boolean: " + value, ex);
    }
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Point getLocation() {
    Response response = execute(DriverCommand.GET_ELEMENT_LOCATION(id));
    Map<String, Object> rawPoint = (Map<String, Object>) response.getValue();
    int x = ((Number) rawPoint.get("x")).intValue();
    int y = ((Number) rawPoint.get("y")).intValue();
    return new Point(x, y);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Dimension getSize() {
    Response response = execute(DriverCommand.GET_ELEMENT_SIZE(id));
    Map<String, Object> rawSize = (Map<String, Object>) response.getValue();
    int width = ((Number) rawSize.get("width")).intValue();
    int height = ((Number) rawSize.get("height")).intValue();
    return new Dimension(width, height);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Rectangle getRect() {
    Response response = execute(DriverCommand.GET_ELEMENT_RECT(id));
    Map<String, Object> rawRect = (Map<String, Object>) response.getValue();
    int x = ((Number) rawRect.get("x")).intValue();
    int y = ((Number) rawRect.get("y")).intValue();
    int width = ((Number) rawRect.get("width")).intValue();
    int height = ((Number) rawRect.get("height")).intValue();
    return new Rectangle(x, y, height, width);
  }

  @Override
  public Coordinates getCoordinates() {
    return new Coordinates() {

      @Override
      public Point onScreen() {
        throw new UnsupportedOperationException("Not supported yet.");
      }

      @Override
      public Point inViewPort() {
        Response response = execute(DriverCommand.GET_ELEMENT_LOCATION_ONCE_SCROLLED_INTO_VIEW(getId()));

        @SuppressWarnings("unchecked")
        Map<String, Number> mapped = (Map<String, Number>) response.getValue();
        return new Point(mapped.get("x").intValue(), mapped.get("y").intValue());
      }

      @Override
      public Point onPage() {
        return getLocation();
      }

      @Override
      public Object getAuxiliary() {
        return getId();
      }
    };
  }

  @Override
  @Beta
  public <X> X getScreenshotAs(OutputType<X> outputType) throws WebDriverException {
    Response response = execute(DriverCommand.ELEMENT_SCREENSHOT(id));
    Object result = response.getValue();
    if (result instanceof String) {
      String base64EncodedPng = (String) result;
      return outputType.convertFromBase64Png(base64EncodedPng);
    } else if (result instanceof byte[]) {
      return outputType.convertFromPngBytes((byte[]) result);
    } else {
      throw new RuntimeException(String.format(
        "Unexpected result for %s command: %s",
        DriverCommand.ELEMENT_SCREENSHOT,
        result == null ? "null" : result.getClass().getName() + " instance"));
    }
  }

  public String toString() {
    if (foundBy == null) {
      return String.format("[%s -> unknown locator]", super.toString());
    }
    return String.format("[%s]", foundBy);
  }

  public Map<String, Object> toJson() {
    return ImmutableMap.of(
      Dialect.OSS.getEncodedElementKey(), getId(),
      Dialect.W3C.getEncodedElementKey(), getId());
  }
}
