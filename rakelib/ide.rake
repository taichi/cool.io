
namespace :ide do
  def xml(name, args={ :version=>"1.0", :encoding=>"UTF-8" }, &block)
    require 'builder'
    puts "make #{name}"
    open(name, 'w') do |file|
      xml = Builder::XmlMarkup.new(:target=>file, :indent=>2)
      xml.instruct! :xml, args
      block.call xml
    end
  end

  file '.project' do |task|
    xml task.name do |x|
      x.projectDescription do
        x.name 'cool.io'
        x.comment
        x.projects
        x.natures do
          x.nature 'org.eclipse.jdt.core.javanature'
        end
        x.buildSpec do
          x.buildCommand do
            x.name 'org.eclipse.jdt.core.javabuilder'
            x.arguments
          end
        end
        x.linkedResources
      end
    end
  end

  file '.classpath' do |task|
    xml task.name do |x|
      x.classpath do
        x.classpathentry :kind=>'output', :path=>'bin'
        x.classpathentry :kind=>'src', :path=>'ext/cool.io'
        x.classpathentry :kind=>'src', :path=>'spec'
        x.classpathentry :kind=>'con', :path=>'org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.8'
        x.classpathentry :kind=>'lib', :path=>".jruby/jruby-#{JRUBY_VERSION}/lib/jruby.jar", :sourcepath=>".jruby/#{JRUBY_VERSION}-src.zip"
        Dir["lib/**/*.jar"].reject { |j| j.end_with? 'coolio_ext.jar'}.each do |p|
          x.classpathentry :kind=>'lib', :exported=>'true', :path=>p
        end
      end
    end
  end
  
  task :launchers do
    mkdir_p 'launcher'
    %w(dns iobuffer stat_watcher tcp_server tcp_socket timer_watcher).each do |n|
      xml "launcher/#{n}_spec.launch", :standalone=>'no' do |x|
        x.launchConfiguration :type=>'org.eclipse.jdt.launching.localJavaApplication' do
          x.listAttribute :key=>'org.eclipse.debug.core.MAPPED_RESOURCE_PATHS' do
            x.listEntry :value=>'/cool.io'
          end
          x.listAttribute :key=>'org.eclipse.debug.core.MAPPED_RESOURCE_TYPES' do
            x.listEntry :value=>4
          end
          x.mapAttribute :key=>'org.eclipse.debug.core.environmentVariables' do
            x.mapEntry :key=>'GEM_HOME', :value=>'.jruby/rubygems'
          end
          x.booleanAttribute :key=>'org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD', :value=>true
          x.stringAttribute :key=>'org.eclipse.jdt.launching.MAIN_TYPE', :value=>'org.jruby.Main'
          x.stringAttribute :key=>'org.eclipse.jdt.launching.PROGRAM_ARGUMENTS', :value=>"-S .jruby/rubygems/bin/rspec spec/#{n}_spec.rb"
          x.stringAttribute :key=>'org.eclipse.jdt.launching.PROJECT_ATTR', :value=>'cool.io'
          x.stringAttribute :key=>'org.eclipse.jdt.launching.VM_ARGUMENTS', :value=>"-Djruby.home=.jruby/jruby-#{JRUBY_VERSION} -Dcool.io.debug=true"
        end
      end
    end
  end
  CLEAN.include "**/*.launch"

  desc 'setup eclipse environment'
  task :eclipse => ['.project', '.classpath', :launchers]
end

