set :css_dir, 'css'
set :js_dir, 'js'
set :images_dir, 'img'

# Build-specific configuration
configure :build do
  set :build_dir, '../public'
  activate :minify_css
end
