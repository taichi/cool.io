require 'bundler/gem_tasks'
require 'rake/clean'

require 'rspec/core/rake_task'
RSpec::Core::RakeTask.new

RSpec::Core::RakeTask.new(:rcov) do |task|
  task.rcov = true
end

task :default => %w(compile spec)

require 'rdoc/task'
Rake::RDocTask.new do |rdoc|
  version = File.exist?('VERSION') ? File.read('VERSION') : ""

  rdoc.rdoc_dir = 'rdoc'
  rdoc.title = "cool.io #{version}"
  rdoc.rdoc_files.include('README*')
  rdoc.rdoc_files.include('lib/**/*.rb')
end

spec = eval(File.read("cool.io.gemspec"))

if defined? JRUBY_VERSION
  if Dir["lib/**/*.jar"].length < 2 && Rake.application.top_level_tasks.include?('copydeps') == false
    warn "At first, you must run below"
    sep = Rake.application.windows? ? File::ALT_SEPARATOR : File::SEPARATOR
    warn ".#{sep}gradlew resolve_deps"
    abort "this task copy dependent libraries from maven repository using gradle."
  end

  desc "make dependent jar file list for jruby"
  dep = "lib/dependencies.rb"
  file dep do |t|
    open "#{t.name}", "w" do |f|
      Dir['ext/**/*.jar'].map { |x| "require \"#{x}\"" }.each do |x|
        f.puts x
      end
    end
  end
  CLEAN.include dep
  
  task :compile => dep

  require "rake/javaextensiontask"
  Rake::JavaExtensionTask.new('coolio_ext', spec) do |ext|
    ext.target_version = '1.8'
    ext.source_version = '1.8 -encoding UTF-8'
    ext.ext_dir = 'ext/cool.io'
    ext.classpath = Dir['ext/**/*.jar'].map { |x| File.expand_path x }.join File::PATH_SEPARATOR
  end
  
  task :build => [:compile]
else
  require 'rake/extensiontask'

  def configure_cross_compilation(ext)
    unless RUBY_PLATFORM =~ /mswin|mingw/
      ext.cross_compile = true
      ext.cross_platform = 'i386-mingw32'#['i386-mswin32-60', 'i386-mingw32']
    end
  end
  Rake::ExtensionTask.new('iobuffer_ext', spec) do |ext|
    ext.ext_dir = 'ext/iobuffer'
    configure_cross_compilation(ext)
  end

  Rake::ExtensionTask.new('http11_client', spec) do |ext|
    ext.ext_dir = 'ext/http11_client'
    configure_cross_compilation(ext)
  end

  Rake::ExtensionTask.new('cool.io_ext', spec) do |ext|
    ext.ext_dir = 'ext/cool.io'
    configure_cross_compilation(ext)
  end

  # Rebuild parser Ragel
  task :http11_parser do
    Dir.chdir "ext/http11_client" do
      target = "http11_parser.c"
      File.unlink target if File.exist? target
      sh "ragel http11_parser.rl | rlgen-cd -G2 -o #{target}"
      raise "Failed to build C source" unless File.exist? target
    end
  end
end

# adapted from http://flavoriffic.blogspot.com/2009/06/easily-valgrind-gdb-your-ruby-c.html
def specs_command
  require "find"
  files = []
  Find.find("spec") do |f|
    files << f if File.basename(f) =~ /.*spec.*\.rb$/
  end
  cmdline = "#{RUBY} -I.:lib:ext:spec \
               -e '%w[#{files.join(' ')}].each { |f| require f }'"
end

namespace :spec do
  desc "run specs with valgrind"
  task :valgrind => :compile do
    system "valgrind --num-callers=15 \
      --partial-loads-ok=yes --undef-value-errors=no \
      --tool=memcheck --leak-check=yes --track-fds=yes \
      --show-reachable=yes #{specs_command}"
  end
end

CLEAN.include "**/*.rbc", "**/*.o", "**/*.so", "**/*.bundle"
CLEAN.exclude "vendor/**/*.rbc", "vendor/**/*.o", "vendor/**/*.so", "vendor/**/*.bundle"
