function show_cirMatrix(cirM)
%show_cirMatrix(cirM)
%   Draw the circle for this matrix on the cent screen

[cent,rad]=matrix2Circle(cirM);

theta = (0:2:360)*pi/180;

x = real(cent);
y = imag(cent);
x_circle = bsxfun(@times,rad,cos(theta));
x_circle = bsxfun(@plus,x_circle,x);
x_circle = cat(2,x_circle,nan(size(x_circle,1),1));
x_circle =  x_circle';
x_circle = x_circle(:);

y_circle = bsxfun(@times,rad,sin(theta));
y_circle = bsxfun(@plus,y_circle,y);
y_circle = cat(2,y_circle,nan(size(y_circle,1),1));
y_circle =  y_circle';
y_circle = y_circle(:);

% plot, maintain hold status
plot(x_circle,y_circle);
axis equal;
hold on;
end
