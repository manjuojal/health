# Customizing Spring Boot Admin Branding

## Logo and Name Customization

The Admin Server has been configured with custom branding:

- **Title**: "Health Monitor Dashboard" (shown in browser tab)
- **Brand**: Custom logo and "Health Monitor" text in navigation header
- **Favicon**: Custom favicon icon

## Custom Logo Location

Place your custom logo files in:
```
src/main/resources/META-INF/spring-boot-admin-server-ui/assets/img/
```

### Current Files:
- `logo.svg` - Main logo (SVG format, scalable)
- `logo.png` - Alternative PNG logo
- `favicon.png` - Browser favicon

## Replacing the Logo

### Option 1: Replace SVG Logo
1. Create your logo as SVG file
2. Save it as `src/main/resources/META-INF/spring-boot-admin-server-ui/assets/img/logo.svg`
3. Ensure it's properly sized (recommended: 200x50px or similar aspect ratio)

### Option 2: Use PNG Logo
1. Create your logo as PNG file (recommended: 200x50px)
2. Save it as `src/main/resources/META-INF/spring-boot-admin-server-ui/assets/img/logo.png`
3. Update `application.yml` to use PNG:
   ```yaml
   spring.boot.admin.ui.brand: '<img src="assets/img/logo.png" style="height: 40px; margin-right: 10px;"><span style="font-size: 20px; font-weight: bold;">Health Monitor</span>'
   ```

## Customizing the Name

Edit `application.yml` to change the displayed name:

```yaml
spring.boot.admin.ui.brand: '<img src="assets/img/logo.svg" style="height: 40px; margin-right: 10px;"><span style="font-size: 20px; font-weight: bold;">Your Custom Name</span>'
```

## Customizing the Title

Change the browser tab title:

```yaml
spring.boot.admin.ui.title: Your Custom Title
```

## Customizing Favicon

1. Create a favicon (recommended: 192x192px PNG)
2. Save it as `src/main/resources/META-INF/spring-boot-admin-server-ui/assets/img/favicon.png`
3. The configuration will automatically use it

## Advanced Customization

You can customize the HTML in the `brand` property:

```yaml
spring.boot.admin.ui.brand: |
  <div style="display: flex; align-items: center;">
    <img src="assets/img/logo.svg" style="height: 40px; margin-right: 10px;">
    <span style="font-size: 20px; font-weight: bold; color: #4CAF50;">Health Monitor</span>
    <span style="font-size: 14px; color: #888; margin-left: 10px;">Dashboard</span>
  </div>
```

## Notes

- SVG logos are recommended for scalability
- Logo should be optimized for web (small file size)
- Recommended logo dimensions: 200x50px or similar aspect ratio
- After making changes, restart the Admin Server to see updates

