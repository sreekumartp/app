# Deployment Guide

This guide covers various deployment options for the Scientific Calculator application.

## Prerequisites

- Node.js 14+ installed
- npm or yarn package manager
- Git (for version control)

## Building for Production

1. Install dependencies:
```bash
npm install
```

2. Create production build:
```bash
npm run build
```

This creates an optimized production build in the `build/` directory.

## Deployment Options

### 1. Static Hosting (Recommended for beginners)

#### Vercel (Easiest)

1. Install Vercel CLI:
```bash
npm install -g vercel
```

2. Deploy:
```bash
vercel
```

3. Follow the prompts to complete deployment

#### Netlify

1. Install Netlify CLI:
```bash
npm install -g netlify-cli
```

2. Build the project:
```bash
npm run build
```

3. Deploy:
```bash
netlify deploy --prod --dir=build
```

#### GitHub Pages

1. Add to `package.json`:
```json
"homepage": "https://yourusername.github.io/app"
```

2. Install gh-pages:
```bash
npm install --save-dev gh-pages
```

3. Add deploy scripts to `package.json`:
```json
"predeploy": "npm run build",
"deploy": "gh-pages -d build"
```

4. Deploy:
```bash
npm run deploy
```

### 2. Using serve (Local Testing)

1. Install serve globally:
```bash
npm install -g serve
```

2. Build the project:
```bash
npm run build
```

3. Serve the build:
```bash
serve -s build
```

Access at http://localhost:3000

### 3. Docker Deployment

1. Create `Dockerfile`:
```dockerfile
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

2. Create `.dockerignore`:
```
node_modules
build
.git
.gitignore
README.md
```

3. Build Docker image:
```bash
docker build -t scientific-calculator .
```

4. Run container:
```bash
docker run -p 80:80 scientific-calculator
```

### 4. AWS S3 + CloudFront

1. Build the project:
```bash
npm run build
```

2. Create S3 bucket:
```bash
aws s3 mb s3://your-bucket-name
```

3. Upload build files:
```bash
aws s3 sync build/ s3://your-bucket-name
```

4. Configure S3 for static website hosting

5. (Optional) Set up CloudFront for CDN

### 5. Heroku

1. Create `static.json`:
```json
{
  "root": "build/",
  "routes": {
    "/**": "index.html"
  }
}
```

2. Add buildpack:
```bash
heroku buildpacks:set mars/create-react-app
```

3. Deploy:
```bash
git push heroku main
```

## Environment Variables

For production deployment, you may want to set environment variables:

1. Create `.env.production`:
```
REACT_APP_VERSION=$npm_package_version
REACT_APP_API_URL=https://api.example.com
```

2. Access in code:
```javascript
const version = process.env.REACT_APP_VERSION;
```

## Performance Optimization

### Enable Compression

Most static hosts enable this automatically, but for custom servers:

**nginx**:
```nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript;
```

**Apache** (.htaccess):
```apache
<IfModule mod_deflate.c>
  AddOutputFilterByType DEFLATE text/html text/css application/javascript
</IfModule>
```

### Cache Headers

**nginx**:
```nginx
location /static/ {
  expires 1y;
  add_header Cache-Control "public, immutable";
}
```

**Apache**:
```apache
<FilesMatch "\.(js|css|png|jpg|jpeg|gif|ico|svg)$">
  Header set Cache-Control "max-age=31536000, public, immutable"
</FilesMatch>
```

## SSL/HTTPS

Most modern hosting platforms (Vercel, Netlify, etc.) provide free SSL certificates automatically.

For custom servers, use [Let's Encrypt](https://letsencrypt.org/):
```bash
certbot --nginx -d yourdomain.com
```

## Post-Deployment Checklist

- [ ] Test all calculator functions
- [ ] Verify theme switching works
- [ ] Check history persistence
- [ ] Test on mobile devices
- [ ] Verify keyboard shortcuts
- [ ] Check console for errors
- [ ] Test in different browsers
- [ ] Verify SSL certificate
- [ ] Check loading performance
- [ ] Test offline functionality (if PWA enabled)

## Monitoring

### Basic Monitoring

1. Google Analytics
2. Sentry for error tracking
3. Lighthouse for performance audits

### Error Tracking Setup (Sentry)

1. Install Sentry:
```bash
npm install @sentry/react
```

2. Initialize in `src/index.js`:
```javascript
import * as Sentry from "@sentry/react";

Sentry.init({
  dsn: "your-sentry-dsn",
  environment: process.env.NODE_ENV,
});
```

## Rollback Strategy

Keep track of deployments:
```bash
# Tag releases
git tag -a v1.0.0 -m "Version 1.0.0"
git push origin v1.0.0

# Rollback if needed
git checkout v1.0.0
npm run build
# Redeploy
```

## Troubleshooting

### Build Fails
- Check Node.js version
- Clear node_modules and reinstall
- Check for syntax errors

### Routes Not Working
- Ensure server is configured for SPA routing
- Add redirect rules for 404 to index.html

### Assets Not Loading
- Check build path configuration
- Verify CORS settings if using CDN
- Check browser console for errors

## Support

For deployment issues:
1. Check hosting platform documentation
2. Review build logs
3. Open an issue in the repository

## Resources

- [Create React App Deployment](https://create-react-app.dev/docs/deployment/)
- [Vercel Documentation](https://vercel.com/docs)
- [Netlify Documentation](https://docs.netlify.com/)
- [AWS S3 Static Hosting](https://docs.aws.amazon.com/AmazonS3/latest/dev/WebsiteHosting.html)
